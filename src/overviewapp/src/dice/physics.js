// Port of the Android dice physics (ui/dice3d/DiceThrowSimulation.kt): deterministic rigid
// bodies with gravity, bounces, Coulomb friction and convex contacts, baked at 120 Hz.
import { dieGeometry } from './geometry.js';

const SAMPLE_RATE = 120;
const STEPS_PER_SAMPLE = 2;
const DT = 1 / (SAMPLE_RATE * STEPS_PER_SAMPLE);
const GRAVITY = 64;
const ITERATIONS = 7;
const FLOOR = { friction: 0.46, restitution: 0.38 };
const WALL = { friction: 0.2, restitution: 0.52 };
const DIE = { friction: 0.18, restitution: 0.42 };
const RESTITUTION_THRESHOLD = 0.9;
const MARGIN = 0.004;
const LINEAR_DAMPING = 0.06;
const ANGULAR_DAMPING = 0.2;
const ROLLING_DAMPING = 0.75;
const SLEEP_LINEAR = 0.09;
const SLEEP_ANGULAR = 0.32;
const SLEEP_SECONDS = 0.16;
const STACK_SLIDE = 16;
const MIN_SECONDS = 0.8;
const ASSIST_START = 2.1;
const ASSIST_RATE = 9;
const MAX_SECONDS = 3.4;

export function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function rotationMatrix(q, out) {
  const [w, x, y, z] = q;
  out[0] = 1 - 2 * (y * y + z * z); out[1] = 2 * (x * y - w * z); out[2] = 2 * (x * z + w * y);
  out[3] = 2 * (x * y + w * z); out[4] = 1 - 2 * (x * x + z * z); out[5] = 2 * (y * z - w * x);
  out[6] = 2 * (x * z - w * y); out[7] = 2 * (y * z + w * x); out[8] = 1 - 2 * (x * x + y * y);
}

export function rotateVector(q, v) {
  const r = new Array(9);
  rotationMatrix(q, r);
  return [r[0] * v[0] + r[1] * v[1] + r[2] * v[2], r[3] * v[0] + r[4] * v[1] + r[5] * v[2], r[6] * v[0] + r[7] * v[1] + r[8] * v[2]];
}

function quatMul(a, b) {
  return [
    a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
    a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
    a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
    a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
  ];
}

function quatBetween(from, to) {
  const d = from[0] * to[0] + from[1] * to[1] + from[2] * to[2];
  if (d > 0.999999) return [1, 0, 0, 0];
  if (d < -0.999999) return [0, 1, 0, 0];
  const c = [from[1] * to[2] - from[2] * to[1], from[2] * to[0] - from[0] * to[2], from[0] * to[1] - from[1] * to[0]];
  const q = [1 + d, ...c];
  const l = Math.hypot(...q);
  return q.map((x) => x / l);
}

function createBody(sides, scale) {
  const g = dieGeometry(sides);
  const n = g.vertices.length;
  const body = {
    sides, g, scale, n,
    local: Float64Array.from(g.vertices.flat().map((x) => x * scale)),
    world: new Float64Array(n * 3),
    faceLocal: Float64Array.from(g.normals.flat()),
    faceWorld: new Float64Array(g.normals.length * 3),
    faceOffset: Float64Array.from(g.offsets.map((o) => o * scale)),
    bound: g.circumradius * scale,
    rest: g.inradius * scale,
    invI: g.inertia.map((i) => 1 / (i * scale * scale)),
    p: [0, 0, 0], v: [0, 0, 0], w: [0, 0, 0], q: [1, 0, 0, 0], r: new Float64Array(9),
    asleep: false, quiet: 0, floor: 0,
  };
  return body;
}

function refresh(b) {
  rotationMatrix(b.q, b.r);
  const r = b.r;
  for (let i = 0; i < b.n; i++) {
    const x = b.local[i * 3]; const y = b.local[i * 3 + 1]; const z = b.local[i * 3 + 2];
    b.world[i * 3] = b.p[0] + r[0] * x + r[1] * y + r[2] * z;
    b.world[i * 3 + 1] = b.p[1] + r[3] * x + r[4] * y + r[5] * z;
    b.world[i * 3 + 2] = b.p[2] + r[6] * x + r[7] * y + r[8] * z;
  }
  for (let i = 0; i < b.faceOffset.length; i++) {
    const x = b.faceLocal[i * 3]; const y = b.faceLocal[i * 3 + 1]; const z = b.faceLocal[i * 3 + 2];
    b.faceWorld[i * 3] = r[0] * x + r[1] * y + r[2] * z;
    b.faceWorld[i * 3 + 1] = r[3] * x + r[4] * y + r[5] * z;
    b.faceWorld[i * 3 + 2] = r[6] * x + r[7] * y + r[8] * z;
  }
}

function invInertia(b, x, y, z) {
  if (b.asleep) return [0, 0, 0];
  const r = b.r;
  const l0 = (r[0] * x + r[3] * y + r[6] * z) * b.invI[0];
  const l1 = (r[1] * x + r[4] * y + r[7] * z) * b.invI[1];
  const l2 = (r[2] * x + r[5] * y + r[8] * z) * b.invI[2];
  return [r[0] * l0 + r[1] * l1 + r[2] * l2, r[3] * l0 + r[4] * l1 + r[5] * l2, r[6] * l0 + r[7] * l1 + r[8] * l2];
}

const invMass = (b) => (b.asleep ? 0 : 1);

function relVel(c, d) {
  const a = c.a; const ra = c.ra;
  let ux = a.v[0] + a.w[1] * ra[2] - a.w[2] * ra[1];
  let uy = a.v[1] + a.w[2] * ra[0] - a.w[0] * ra[2];
  let uz = a.v[2] + a.w[0] * ra[1] - a.w[1] * ra[0];
  if (c.b) {
    const b = c.b; const rb = c.rb;
    ux -= b.v[0] + b.w[1] * rb[2] - b.w[2] * rb[1];
    uy -= b.v[1] + b.w[2] * rb[0] - b.w[0] * rb[2];
    uz -= b.v[2] + b.w[0] * rb[1] - b.w[1] * rb[0];
  }
  return ux * d[0] + uy * d[1] + uz * d[2];
}

function effMass(c, d) {
  const cr = (r) => [r[1] * d[2] - r[2] * d[1], r[2] * d[0] - r[0] * d[2], r[0] * d[1] - r[1] * d[0]];
  let k = invMass(c.a);
  let x = cr(c.ra); let i = invInertia(c.a, ...x);
  k += x[0] * i[0] + x[1] * i[1] + x[2] * i[2];
  if (c.b) {
    k += invMass(c.b);
    x = cr(c.rb); i = invInertia(c.b, ...x);
    k += x[0] * i[0] + x[1] * i[1] + x[2] * i[2];
  }
  return 1 / Math.max(k, 1e-9);
}

function impulse(c, j) {
  const a = c.a; const ma = invMass(a);
  a.v[0] += j[0] * ma; a.v[1] += j[1] * ma; a.v[2] += j[2] * ma;
  const ra = c.ra;
  const ia = invInertia(a, ra[1] * j[2] - ra[2] * j[1], ra[2] * j[0] - ra[0] * j[2], ra[0] * j[1] - ra[1] * j[0]);
  a.w[0] += ia[0]; a.w[1] += ia[1]; a.w[2] += ia[2];
  if (c.b) {
    const b = c.b; const mb = invMass(b); const rb = c.rb;
    b.v[0] -= j[0] * mb; b.v[1] -= j[1] * mb; b.v[2] -= j[2] * mb;
    const ib = invInertia(b, rb[1] * j[2] - rb[2] * j[1], rb[2] * j[0] - rb[0] * j[2], rb[0] * j[1] - rb[1] * j[0]);
    b.w[0] -= ib[0]; b.w[1] -= ib[1]; b.w[2] -= ib[2];
  }
}

function contact(a, b, n, ra, rb, gap, material) {
  const t1 = Math.abs(n[0]) < 0.57 ? [0, n[2], -n[1]] : [-n[2], 0, n[0]];
  const tl = Math.hypot(...t1);
  for (let i = 0; i < 3; i++) t1[i] /= tl;
  const t2 = [n[1] * t1[2] - n[2] * t1[1], n[2] * t1[0] - n[0] * t1[2], n[0] * t1[1] - n[1] * t1[0]];
  const c = { a, b, n, ra, rb, t1, t2, jn: 0, jt1: 0, jt2: 0, friction: material.friction };
  c.mn = effMass(c, n); c.m1 = effMass(c, t1); c.m2 = effMass(c, t2);
  const un = relVel(c, n);
  c.target = un < -RESTITUTION_THRESHOLD ? -material.restitution * un : gap > 0 ? -gap / DT : 0;
  return c;
}

function solve(c) {
  const un = relVel(c, c.n);
  const jn = Math.max(0, c.jn + (c.target - un) * c.mn);
  const dn = jn - c.jn; c.jn = jn;
  if (dn) impulse(c, c.n.map((x) => x * dn));
  const limit = c.friction * c.jn;
  for (const [t, key, m] of [[c.t1, 'jt1', c.m1], [c.t2, 'jt2', c.m2]]) {
    const u = relVel(c, t);
    const j = Math.min(limit, Math.max(-limit, c[key] - u * m));
    const d = j - c[key]; c[key] = j;
    if (d) impulse(c, t.map((x) => x * d));
  }
}

function deepest(owner, hull) {
  let depth = 0; let normal = null;
  for (let i = 0; i < owner.n; i++) {
    const x = owner.world[i * 3] - hull.p[0]; const y = owner.world[i * 3 + 1] - hull.p[1]; const z = owner.world[i * 3 + 2] - hull.p[2];
    let best = -Infinity; let face = -1;
    for (let f = 0; f < hull.faceOffset.length; f++) {
      const d = hull.faceWorld[f * 3] * x + hull.faceWorld[f * 3 + 1] * y + hull.faceWorld[f * 3 + 2] * z - hull.faceOffset[f];
      if (d >= MARGIN) { face = -1; break; }
      if (d > best) { best = d; face = f; }
    }
    if (face >= 0) {
      const contactNormal = [hull.faceWorld[face * 3], hull.faceWorld[face * 3 + 1], hull.faceWorld[face * 3 + 2]];
      if (best < depth) { depth = best; normal = contactNormal; }
      if (owner.onContact) owner.onContact(i, contactNormal, best);
    }
  }
  return { depth, normal };
}

/**
 * Bakes a throw of [sides] dice. Returns sampled poses (x, y, z, qw, qx, qy, qz per die and
 * sample), the duration and the face each die physically shows on top.
 */
export function bakeThrow(sidesList, { seed = Date.now(), scale = 0.72, halfWidth = 4.2, halfHeight = 2.6 } = {}) {
  const rand = mulberry32(seed);
  const range = (a, b) => a + rand() * (b - a);
  const bodies = sidesList.map((sides) => createBody(sides, scale));
  const spacing = Math.max(...bodies.map((b) => b.bound)) * 2.2;
  bodies.forEach((b, i) => {
    // Thrown from the right edge of the felt towards the left, like a hand releasing a handful.
    const column = i % 2; const row = Math.floor(i / 2);
    b.p = [halfWidth - b.bound * 1.2 - column * spacing, -halfHeight + b.bound * 1.2 + row * spacing * 0.98, 1.6 + range(0, 0.6)];
    if (b.p[1] > halfHeight - b.bound) b.p[1] = halfHeight - b.bound;
    const speed = range(7.4, 9.6);
    const heading = Math.PI + range(-0.35, 0.35) + (b.p[1] / halfHeight) * -0.25;
    b.v = [Math.cos(heading) * speed, Math.sin(heading) * speed, range(0.8, 2.4)];
    const u1 = rand(); const u2 = rand(); const u3 = rand();
    const s1 = Math.sqrt(1 - u1); const s2 = Math.sqrt(u1);
    b.q = [s2 * Math.cos(2 * Math.PI * u3), s1 * Math.sin(2 * Math.PI * u2), s1 * Math.cos(2 * Math.PI * u2), s2 * Math.sin(2 * Math.PI * u3)];
    const sp = Math.hypot(b.v[0], b.v[1]);
    const axis = [-b.v[1] / sp * 0.8 + (rand() - 0.5) * 1.1, b.v[0] / sp * 0.8 + (rand() - 0.5) * 1.1, (rand() - 0.5) * 1.3];
    const al = Math.hypot(...axis); const spin = 13 + rand() * 12;
    b.w = axis.map((x) => (x / al) * spin);
    refresh(b);
  });

  const frames = [];
  const snapshot = () => frames.push(bodies.flatMap((b) => [...b.p, ...b.q]));
  snapshot();
  let time = 0; let steps = 0;
  while (true) {
    const contacts = [];
    for (const b of bodies) {
      if (!b.asleep) b.v[2] -= GRAVITY * DT;
      b.floor = 0;
      if (b.asleep) continue;
      for (let i = 0; i < b.n; i++) {
        const x = b.world[i * 3]; const y = b.world[i * 3 + 1]; const z = b.world[i * 3 + 2];
        const ra = [x - b.p[0], y - b.p[1], z - b.p[2]];
        if (z < MARGIN) { b.floor++; contacts.push(contact(b, null, [0, 0, 1], ra, null, z, FLOOR)); }
        if (x + halfWidth < MARGIN) contacts.push(contact(b, null, [1, 0, 0], ra, null, x + halfWidth, WALL));
        if (halfWidth - x < MARGIN) contacts.push(contact(b, null, [-1, 0, 0], ra, null, halfWidth - x, WALL));
        if (y + halfHeight < MARGIN) contacts.push(contact(b, null, [0, 1, 0], ra, null, y + halfHeight, WALL));
        if (halfHeight - y < MARGIN) contacts.push(contact(b, null, [0, -1, 0], ra, null, halfHeight - y, WALL));
      }
    }
    for (let i = 0; i < bodies.length; i++) for (let j = i + 1; j < bodies.length; j++) {
      const a = bodies[i]; const b = bodies[j];
      if (a.asleep && b.asleep) continue;
      const reach = a.bound + b.bound + MARGIN;
      if ((b.p[0] - a.p[0]) ** 2 + (b.p[1] - a.p[1]) ** 2 + (b.p[2] - a.p[2]) ** 2 > reach * reach) continue;
      const before = contacts.length;
      for (const [owner, hull] of [[a, b], [b, a]]) {
        owner.onContact = (vi, n, gap) => {
          const x = owner.world[vi * 3]; const y = owner.world[vi * 3 + 1]; const z = owner.world[vi * 3 + 2];
          const approach = (owner.v[0] - hull.v[0]) * n[0] + (owner.v[1] - hull.v[1]) * n[1] + (owner.v[2] - hull.v[2]) * n[2];
          if (approach < -0.7) { owner.asleep = false; hull.asleep = false; }
          contacts.push(contact(owner, hull, n, [x - owner.p[0], y - owner.p[1], z - owner.p[2]], [x - hull.p[0], y - hull.p[1], z - hull.p[2]], gap, DIE));
        };
        deepest(owner, hull);
        owner.onContact = null;
      }
      if (contacts.length > before) {
        const upper = a.p[2] >= b.p[2] ? a : b; const lower = upper === a ? b : a;
        if (!upper.asleep && upper.p[2] - lower.p[2] > 0.55 * (upper.rest + lower.rest)) {
          const dx = upper.p[0] - lower.p[0]; const dy = upper.p[1] - lower.p[1]; const pl = Math.hypot(dx, dy);
          upper.v[0] += (pl > 1e-4 ? dx / pl : 1) * STACK_SLIDE * DT;
          upper.v[1] += (pl > 1e-4 ? dy / pl : 0) * STACK_SLIDE * DT;
        }
      }
    }
    for (let k = 0; k < ITERATIONS; k++) for (const c of contacts) solve(c);
    for (const b of bodies) {
      if (b.asleep) continue;
      b.p = [b.p[0] + b.v[0] * DT, b.p[1] + b.v[1] * DT, b.p[2] + b.v[2] * DT];
      const dq = quatMul([0, ...b.w], b.q);
      b.q = b.q.map((x, i) => x + 0.5 * DT * dq[i]);
      const ql = Math.hypot(...b.q);
      b.q = b.q.map((x) => x / ql);
      refresh(b);
      let floor = 0; let left = 0; let right = 0; let bottom = 0; let top = 0;
      for (let i = 0; i < b.n; i++) {
        floor = Math.min(floor, b.world[i * 3 + 2]);
        left = Math.min(left, b.world[i * 3] + halfWidth); right = Math.min(right, halfWidth - b.world[i * 3]);
        bottom = Math.min(bottom, b.world[i * 3 + 1] + halfHeight); top = Math.min(top, halfHeight - b.world[i * 3 + 1]);
      }
      const shift = [-left + right, -bottom + top, -floor];
      if (shift.some((x) => x)) { b.p = b.p.map((x, i) => x + shift[i]); refresh(b); }
    }
    for (let i = 0; i < bodies.length; i++) for (let j = i + 1; j < bodies.length; j++) {
      const a = bodies[i]; const b = bodies[j];
      if (a.asleep && b.asleep) continue;
      const first = deepest(a, b); const second = deepest(b, a);
      const pick = first.depth <= second.depth ? { ...first, sign: 1 } : { ...second, sign: -1 };
      if (!pick.normal || pick.depth >= -0.0005) continue;
      const wa = invMass(a); const wb = invMass(b); const total = wa + wb;
      if (!total) continue;
      const push = (-pick.depth - 0.0005) * 0.8 * pick.sign;
      a.p = a.p.map((x, k) => x + pick.normal[k] * push * wa / total);
      b.p = b.p.map((x, k) => x - pick.normal[k] * push * wb / total);
      refresh(a); refresh(b);
    }
    for (const b of bodies) {
      if (b.asleep) continue;
      let linear = Math.exp(-LINEAR_DAMPING * DT); let angular = Math.exp(-ANGULAR_DAMPING * DT);
      if (b.floor > 0) angular *= Math.exp(-ROLLING_DAMPING * DT);
      if (time > ASSIST_START) { const extra = Math.exp(-(time - ASSIST_START) * ASSIST_RATE * DT); linear *= extra; angular *= extra; }
      b.v[0] *= linear; b.v[1] *= linear;
      b.w = b.w.map((x) => x * angular);
      const resting = b.floor >= 3 || (b.floor >= 1 && time > ASSIST_START);
      if (resting && Math.hypot(...b.v) < SLEEP_LINEAR && Math.hypot(...b.w) < SLEEP_ANGULAR) {
        b.quiet += DT;
        if (b.quiet >= SLEEP_SECONDS) { b.asleep = true; b.v = [0, 0, 0]; b.w = [0, 0, 0]; }
      } else b.quiet = 0;
    }
    time += DT; steps++;
    if (steps % STEPS_PER_SAMPLE === 0) snapshot();
    if ((time >= MIN_SECONDS && bodies.every((b) => b.asleep)) || time >= MAX_SECONDS) break;
  }

  // Settle exactly flat on the face pointing down (imperceptible for dice already at rest).
  const settled = bodies.map((b) => {
    let down = b.g.normals[0]; let lowest = Infinity;
    for (const n of b.g.normals) { const z = rotateVector(b.q, n)[2]; if (z < lowest) { lowest = z; down = n; } }
    const q = quatMul(quatBetween(rotateVector(b.q, down), [0, 0, -1]), b.q);
    const minZ = Math.min(...b.g.vertices.map((v) => rotateVector(q, v)[2])) * b.scale;
    return { p: [b.p[0], b.p[1], -minZ], q };
  });
  const tail = 18;
  const start = bodies.map((b) => ({ p: [...b.p], q: [...b.q] }));
  // Final safety net, as on Android: separate dice that ended too close and keep them on the felt.
  for (let iteration = 0; iteration < 48; iteration++) {
    let moved = false;
    bodies.forEach((b, i) => { b.p = [...settled[i].p]; b.q = [...settled[i].q]; refresh(b); });
    for (let i = 0; i < bodies.length; i++) for (let j = i + 1; j < bodies.length; j++) {
      const a = bodies[i]; const b = bodies[j];
      const dx = b.p[0] - a.p[0]; const dy = b.p[1] - a.p[1];
      const distance = Math.hypot(dx, dy, b.p[2] - a.p[2]);
      if (distance >= a.bound + b.bound) continue;
      const depth = Math.max(a.rest + b.rest - distance, -deepest(a, b).depth, -deepest(b, a).depth);
      if (depth <= 1e-4) continue;
      const planar = Math.hypot(dx, dy);
      const ux = planar > 1e-4 ? dx / planar : Math.cos(i * 2.4 + j + iteration);
      const uy = planar > 1e-4 ? dy / planar : Math.sin(i * 2.4 + j + iteration);
      const push = (depth + 0.01) / 2;
      settled[i].p = [settled[i].p[0] - ux * push, settled[i].p[1] - uy * push, settled[i].p[2]];
      settled[j].p = [settled[j].p[0] + ux * push, settled[j].p[1] + uy * push, settled[j].p[2]];
      a.p = [...settled[i].p]; b.p = [...settled[j].p]; refresh(a); refresh(b);
      moved = true;
    }
    bodies.forEach((b, i) => {
      let minX = Infinity; let maxX = -Infinity; let minY = Infinity; let maxY = -Infinity;
      for (const v of b.g.vertices) {
        const w = rotateVector(settled[i].q, v);
        const x = settled[i].p[0] + w[0] * b.scale; const y = settled[i].p[1] + w[1] * b.scale;
        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
      }
      const sx = minX < -halfWidth ? -halfWidth - minX : maxX > halfWidth ? halfWidth - maxX : 0;
      const sy = minY < -halfHeight ? -halfHeight - minY : maxY > halfHeight ? halfHeight - maxY : 0;
      if (sx || sy) { settled[i].p = [settled[i].p[0] + sx, settled[i].p[1] + sy, settled[i].p[2]]; moved = true; }
    });
    if (!moved) break;
  }
  for (let s = 1; s <= tail; s++) {
    const t = s / tail; const e = t * t * (3 - 2 * t);
    frames.push(bodies.flatMap((b, i) => {
      const from = start[i]; const to = settled[i];
      const sign = from.q.reduce((acc, x, k) => acc + x * to.q[k], 0) < 0 ? -1 : 1;
      const q = from.q.map((x, k) => x * (1 - e) + to.q[k] * sign * e);
      const ql = Math.hypot(...q);
      const qn = q.map((x) => x / ql);
      const p = from.p.map((x, k) => x + (to.p[k] - x) * e);
      const lowest = Math.min(...b.g.vertices.map((v) => rotateVector(qn, v)[2])) * b.scale;
      if (p[2] + lowest < 0) p[2] = -lowest;
      return [...p, ...qn];
    }));
  }
  const results = bodies.map((b, i) => {
    const q = settled[i].q;
    let best = null; let bestZ = -Infinity;
    for (const anchor of b.g.anchors) { const z = rotateVector(q, anchor.dir)[2]; if (z > bestZ) { bestZ = z; best = anchor.label; } }
    return best;
  });
  return { frames, sampleRate: SAMPLE_RATE, duration: (frames.length - 1) / SAMPLE_RATE, results, scale };
}

/** Interpolated pose of die [index] at [time] seconds. */
export function poseAt(baked, index, time) {
  const cursor = Math.max(0, time) * baked.sampleRate;
  const i0 = Math.min(baked.frames.length - 1, Math.floor(cursor));
  const i1 = Math.min(baked.frames.length - 1, i0 + 1);
  const f = Math.min(1, Math.max(0, cursor - i0));
  const a = baked.frames[i0].slice(index * 7, index * 7 + 7);
  const b = baked.frames[i1].slice(index * 7, index * 7 + 7);
  const sign = a[3] * b[3] + a[4] * b[4] + a[5] * b[5] + a[6] * b[6] < 0 ? -1 : 1;
  const q = [3, 4, 5, 6].map((k) => a[k] * (1 - f) + b[k] * sign * f);
  const ql = Math.hypot(...q);
  return { position: [0, 1, 2].map((k) => a[k] + (b[k] - a[k]) * f), quaternion: q.map((x) => x / ql) };
}
