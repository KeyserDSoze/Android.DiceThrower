// Die geometry shared by the website hero: exact hulls, realistic numbering, rounded render
// meshes and numeral placement. Mirrors the Android renderer (ui/dice3d/DieGeometry.kt and
// DiceMeshFactory.kt) so the website shows the same dice the app throws.

const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const len = (a) => Math.sqrt(dot(a, a));
const norm = (a) => { const l = Math.max(1e-9, len(a)); return [a[0] / l, a[1] / l, a[2] / l]; };

export const vec = { add, sub, mul, dot, cross, len, norm };

function slerpUnit(a, b, t) {
  const d = Math.min(1, Math.max(-1, dot(a, b)));
  const angle = Math.acos(d);
  if (angle < 1e-4) return norm(add(mul(a, 1 - t), mul(b, t)));
  const s = Math.sin(angle);
  return norm(add(mul(a, Math.sin((1 - t) * angle) / s), mul(b, Math.sin(t * angle) / s)));
}

const normalizeRadius = (points) => {
  const max = Math.max(...points.map(len));
  return points.map((p) => mul(p, 1 / max));
};

function faceNormal(vertices, face) {
  const points = face.map((i) => vertices[i]);
  const centroid = mul(points.reduce(add), 1 / points.length);
  let n = norm(cross(sub(points[1], points[0]), sub(points[2], points[0])));
  if (dot(n, centroid) < 0) n = mul(n, -1);
  return n;
}

function orient(vertices, face) {
  const n = cross(sub(vertices[face[1]], vertices[face[0]]), sub(vertices[face[2]], vertices[face[0]]));
  const centroid = mul(face.map((i) => vertices[i]).reduce(add), 1 / face.length);
  return dot(n, centroid) < 0 ? [...face].reverse() : face;
}

function oppositeLabels(vertices, faces, pairs) {
  const normals = faces.map((f) => faceNormal(vertices, f));
  const labels = new Array(faces.length).fill(null);
  let previous = -1;
  for (const [first, second] of pairs) {
    const free = faces.map((_, i) => i).filter((i) => labels[i] === null);
    const chosen = previous < 0 ? free[0] : free.reduce((best, i) => (dot(normals[i], normals[previous]) < dot(normals[best], normals[previous]) ? i : best), free[0]);
    labels[chosen] = first;
    const rest = faces.map((_, i) => i).filter((i) => labels[i] === null);
    const opposite = rest.reduce((best, i) => (dot(normals[i], normals[chosen]) < dot(normals[best], normals[chosen]) ? i : best), rest[0]);
    labels[opposite] = second;
    previous = chosen;
  }
  return labels;
}

const sumPairs = (n) => Array.from({ length: n / 2 }, (_, i) => [String(i + 1), String(n - i)]);

function inscribedCircle(points, normal) {
  const centroid = mul(points.reduce(add), 1 / points.length);
  const toFirst = sub(points[0], centroid);
  const u = norm(sub(toFirst, mul(normal, dot(toFirst, normal))));
  const v = cross(normal, u);
  const flat = points.map((p) => { const d = sub(p, centroid); return [dot(d, u), dot(d, v)]; });
  const clearance = (x, y) => Math.min(...flat.map((a, i) => {
    const b = flat[(i + 1) % flat.length];
    const ex = b[0] - a[0]; const ey = b[1] - a[1];
    return (ex * (y - a[1]) - ey * (x - a[0])) / Math.hypot(ex, ey);
  }));
  let x = 0; let y = 0; let best = clearance(0, 0);
  let step = Math.max(...flat.map(([a, b]) => Math.hypot(a, b))) * 0.25;
  while (step > 1e-5) {
    let improved = false;
    for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) {
      if (!dx && !dy) continue;
      const c = clearance(x + dx * step, y + dy * step);
      if (c > best + 1e-7) { best = c; x += dx * step; y += dy * step; improved = true; }
    }
    if (!improved) step *= 0.5;
  }
  return { center: add(centroid, add(mul(u, x), mul(v, y))), radius: Math.abs(best) };
}

function build(sides, vertices, rawFaces, { labels, vertexLabels, upMode = 'vertex', fill = 0.8, bevel = 0.06 }) {
  const faces = rawFaces.map((f) => orient(vertices, f));
  const normals = faces.map((f) => faceNormal(vertices, f));
  const offsets = faces.map((f, i) => dot(normals[i], vertices[f[0]]));
  const glyphs = [];
  let anchors = [];
  if (vertexLabels) {
    anchors = vertices.map((p, i) => ({ dir: norm(p), label: vertexLabels[i] }));
    faces.forEach((face, fi) => {
      const pts = face.map((i) => vertices[i]);
      const centroid = mul(pts.reduce(add), 1 / 3);
      const apothem = len(sub(mul(add(pts[0], pts[1]), 0.5), centroid));
      face.forEach((vi) => {
        const up = norm(sub(vertices[vi], centroid));
        glyphs.push({ center: add(centroid, mul(sub(vertices[vi], centroid), 0.5)), up, right: norm(cross(up, normals[fi])), normal: normals[fi], size: apothem * 1.02, label: vertexLabels[vi] });
      });
    });
  } else {
    rawFaces.forEach((authored, fi) => {
      if (labels[fi] == null) return;
      const n = normals[fi];
      const { center, radius } = inscribedCircle(faces[fi].map((i) => vertices[i]), n);
      const target = upMode === 'edge' ? mul(add(vertices[authored[0]], vertices[authored[1]]), 0.5) : vertices[authored[0]];
      const toTarget = sub(target, center);
      const up = norm(sub(toTarget, mul(n, dot(toTarget, n))));
      glyphs.push({ center, up, right: norm(cross(up, n)), normal: n, size: radius * 2 * fill, label: labels[fi] });
      anchors.push({ dir: n, label: labels[fi] });
    });
  }
  const circumradius = Math.max(...vertices.map(len));
  return { sides, vertices, faces, normals, offsets, anchors, glyphs, bevel, circumradius, inradius: Math.min(...offsets), inertia: inertiaOf(vertices, faces) };
}

function inertiaOf(vertices, faces) {
  let volume = 0;
  const s = new Array(9).fill(0);
  for (const face of faces) {
    for (let i = 1; i < face.length - 1; i++) {
      const a = vertices[face[0]]; const b = vertices[face[i]]; const c = vertices[face[i + 1]];
      const v = dot(a, cross(b, c)) / 6;
      volume += v;
      const sum = add(add(a, b), c);
      for (let r = 0; r < 3; r++) for (let k = 0; k < 3; k++) {
        s[r * 3 + k] += (v / 20) * (sum[r] * sum[k] + a[r] * a[k] + b[r] * b[k] + c[r] * c[k]);
      }
    }
  }
  const trace = s[0] + s[4] + s[8];
  return [(trace - s[0]) / volume, (trace - s[4]) / volume, (trace - s[8]) / volume];
}

function icosahedronGeometry() {
  const phi = (1 + Math.sqrt(5)) / 2;
  const vertices = normalizeRadius([
    [-1, phi, 0], [1, phi, 0], [-1, -phi, 0], [1, -phi, 0], [0, -1, phi], [0, 1, phi],
    [0, -1, -phi], [0, 1, -phi], [phi, 0, -1], [phi, 0, 1], [-phi, 0, -1], [-phi, 0, 1],
  ]);
  const faces = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8],
    [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]];
  return { vertices, faces };
}

function makeDie(sides) {
  switch (sides) {
    case 4: {
      const vertices = normalizeRadius([[1, 1, 1], [-1, -1, 1], [-1, 1, -1], [1, -1, -1]]);
      return build(4, vertices, [[0, 1, 2], [0, 3, 1], [0, 2, 3], [1, 3, 2]], { vertexLabels: ['1', '2', '3', '4'], bevel: 0.07 });
    }
    case 6: {
      const vertices = normalizeRadius([[-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1], [-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]]);
      const faces = [[0, 3, 2, 1], [4, 5, 6, 7], [0, 4, 7, 3], [1, 2, 6, 5], [3, 7, 6, 2], [0, 1, 5, 4]];
      return build(6, vertices, faces, { labels: oppositeLabels(vertices, faces, sumPairs(6)), upMode: 'edge', fill: 0.7, bevel: 0.095 });
    }
    case 8: {
      const vertices = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
      const faces = [[2, 0, 4], [2, 4, 1], [2, 1, 5], [2, 5, 0], [3, 4, 0], [3, 1, 4], [3, 5, 1], [3, 0, 5]];
      return build(8, vertices, faces, { labels: oppositeLabels(vertices, faces, sumPairs(8)), fill: 0.84, bevel: 0.072 });
    }
    case 10: case 100: {
      const halfHeight = 1.16; const ringRadius = 0.94; const ringHeight = halfHeight * 0.10557281;
      const raw = [[0, halfHeight, 0], [0, -halfHeight, 0]];
      for (let i = 0; i < 10; i++) {
        const a = (2 * Math.PI * i) / 10 - Math.PI / 2;
        raw.push([Math.cos(a) * ringRadius, i % 2 === 0 ? ringHeight : -ringHeight, Math.sin(a) * ringRadius]);
      }
      const faces = [];
      for (let i = 0; i < 5; i++) {
        const high = 2 + (2 * i) % 10; const low = 2 + (2 * i + 1) % 10;
        const highNext = 2 + (2 * i + 2) % 10; const lowNext = 2 + (2 * i + 3) % 10;
        faces.push([0, high, low, highNext], [1, lowNext, highNext, low]);
      }
      const vertices = normalizeRadius(raw);
      const normals = faces.map((f) => faceNormal(vertices, f));
      const labels = new Array(10).fill(null);
      const top = sides === 100 ? ['00', '40', '80', '20', '60'] : ['1', '5', '9', '3', '7'];
      const opposite = (l) => (sides === 100 ? String(90 - Number(l)).padStart(2, '0') : String(11 - Number(l)));
      faces.map((f, i) => i).filter((i) => faces[i].includes(0))
        .sort((a, b) => Math.atan2(normals[a][2], normals[a][0]) - Math.atan2(normals[b][2], normals[b][0]))
        .forEach((fi, order) => {
          labels[fi] = top[order];
          const opp = normals.reduce((best, n, i) => (dot(n, normals[fi]) < dot(normals[best], normals[fi]) ? i : best), 0);
          labels[opp] = opposite(top[order]);
        });
      return build(sides, vertices, faces, { labels, fill: sides === 100 ? 0.92 : 0.88, bevel: 0.06 });
    }
    case 12: {
      const ico = icosahedronGeometry();
      const dual = ico.faces.map((f) => norm(mul(f.map((i) => ico.vertices[i]).reduce(add), 1 / 3)));
      const pentagons = ico.vertices.map((vertex, vi) => {
        const adjacent = ico.faces.map((f, i) => i).filter((i) => ico.faces[i].includes(vi));
        const axis = norm(vertex);
        const ref = Math.abs(axis[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
        const t = norm(cross(ref, axis)); const b = norm(cross(axis, t));
        return adjacent.sort((p, q) => Math.atan2(dot(dual[p], b), dot(dual[p], t)) - Math.atan2(dot(dual[q], b), dot(dual[q], t)));
      });
      const vertices = normalizeRadius(dual);
      return build(12, vertices, pentagons, { labels: oppositeLabels(vertices, pentagons, sumPairs(12)), fill: 0.8, bevel: 0.065 });
    }
    case 20:
    default: {
      const { vertices, faces } = icosahedronGeometry();
      return build(20, vertices, faces, { labels: oppositeLabels(vertices, faces, sumPairs(20)), fill: 0.8, bevel: 0.065 });
    }
  }
}

const cache = new Map();
export function dieGeometry(sides) {
  if (!cache.has(sides)) cache.set(sides, makeDie(sides));
  return cache.get(sides);
}

/** Label of the anchor pointing most upwards (+Z) for a rotation function. */
export function topLabel(geometry, rotate) {
  let best = null; let bestZ = -Infinity;
  for (const anchor of geometry.anchors) {
    const z = rotate(anchor.dir)[2];
    if (z > bestZ) { bestZ = z; best = anchor.label; }
  }
  return best;
}

function offsetVertex(planes, fallback) {
  const m = new Array(9).fill(0); const rhs = [0, 0, 0];
  for (const [n, d] of planes) {
    for (let r = 0; r < 3; r++) { for (let c = 0; c < 3; c++) m[r * 3 + c] += n[r] * n[c]; rhs[r] += n[r] * d; }
  }
  const det3 = (a) => a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6]);
  const det = det3(m);
  if (Math.abs(det) < 1e-12) return fallback;
  const solve = (col) => { const c = [...m]; for (let r = 0; r < 3; r++) c[r * 3 + col] = rhs[r]; return det3(c) / det; };
  return [solve(0), solve(1), solve(2)];
}

/** Rounded die = (hull shrunk by r) ⊕ sphere(r), with smooth normals on edges and corners. */
export function roundedBody(geometry) {
  const { vertices, faces, normals, offsets, bevel: r } = geometry;
  const positions = []; const outNormals = [];
  const tri = (p0, p1, p2, n0, n1, n2) => {
    const g = cross(sub(p1, p0), sub(p2, p0));
    if (dot(g, g) < 1e-14) return;
    const flip = dot(g, add(add(n0, n1), n2)) < 0;
    const order = flip ? [[p0, n0], [p2, n2], [p1, n1]] : [[p0, n0], [p1, n1], [p2, n2]];
    for (const [p, n] of order) { positions.push(...p); outNormals.push(...n); }
  };
  const facesOf = vertices.map((_, v) => faces.map((f, i) => i).filter((i) => faces[i].includes(v)));
  const inner = vertices.map((p, v) => offsetVertex(facesOf[v].map((f) => [normals[f], offsets[f] - r]), p));
  const steps = (a, b) => Math.min(6, Math.max(1, Math.ceil(Math.acos(Math.min(1, Math.max(-1, dot(a, b)))) / 0.4)));
  const arc = (fa, fb, s, k) => (fa < fb ? slerpUnit(normals[fa], normals[fb], s / k) : slerpUnit(normals[fb], normals[fa], (k - s) / k));
  faces.forEach((face, fi) => {
    const pts = face.map((v) => add(inner[v], mul(normals[fi], r)));
    for (let i = 1; i < pts.length - 1; i++) tri(pts[0], pts[i], pts[i + 1], normals[fi], normals[fi], normals[fi]);
  });
  const edges = new Map();
  faces.forEach((face, fi) => face.forEach((a, k) => {
    const b = face[(k + 1) % face.length];
    const key = Math.min(a, b) + ':' + Math.max(a, b);
    if (!edges.has(key)) edges.set(key, []);
    edges.get(key).push(fi);
  }));
  for (const [key, fs] of edges) {
    if (fs.length !== 2) continue;
    const [a, b] = key.split(':').map(Number);
    const f1 = Math.min(...fs); const f2 = Math.max(...fs); const k = steps(normals[f1], normals[f2]);
    for (let s = 0; s < k; s++) {
      const n0 = arc(f1, f2, s, k); const n1 = arc(f1, f2, s + 1, k);
      const a0 = add(inner[a], mul(n0, r)); const b0 = add(inner[b], mul(n0, r));
      const a1 = add(inner[a], mul(n1, r)); const b1 = add(inner[b], mul(n1, r));
      tri(a0, b0, b1, n0, n0, n1); tri(a0, b1, a1, n0, n1, n1);
    }
  }
  vertices.forEach((_, v) => {
    const fs = facesOf[v];
    if (fs.length < 3) return;
    const axis = norm(fs.map((f) => normals[f]).reduce(add));
    const helper = Math.abs(axis[0]) < 0.9 ? [1, 0, 0] : [0, 1, 0];
    const t = norm(cross(helper, axis)); const bt = cross(axis, t);
    const ordered = [...fs].sort((p, q) => Math.atan2(dot(normals[p], bt), dot(normals[p], t)) - Math.atan2(dot(normals[q], bt), dot(normals[q], t)));
    const c = inner[v];
    ordered.forEach((fa, i) => {
      const fb = ordered[(i + 1) % ordered.length]; const k = steps(normals[fa], normals[fb]);
      for (let s = 0; s < k; s++) {
        const p0 = arc(fa, fb, s, k); const p1 = arc(fa, fb, s + 1, k);
        const m0 = slerpUnit(axis, p0, 0.5); const m1 = slerpUnit(axis, p1, 0.5);
        tri(add(c, mul(axis, r)), add(c, mul(m0, r)), add(c, mul(m1, r)), axis, m0, m1);
        tri(add(c, mul(m0, r)), add(c, mul(p0, r)), add(c, mul(p1, r)), m0, p0, p1);
        tri(add(c, mul(m0, r)), add(c, mul(p1, r)), add(c, mul(m1, r)), m0, p1, m1);
      }
    });
  });
  return { positions: new Float32Array(positions), normals: new Float32Array(outNormals) };
}

export const ATLAS_COLUMNS = 6;
export const ATLAS_LABELS = [...new Set([...Array.from({ length: 20 }, (_, i) => String(i + 1)), ...Array.from({ length: 10 }, (_, i) => `${i}0`)])];

/** Numeral quads lifted just above each face, with atlas UVs (row 0 = top of the canvas). */
export function glyphQuads(geometry) {
  const positions = []; const normals = []; const uvs = [];
  for (const g of geometry.glyphs) {
    const c = add(g.center, mul(g.normal, 0.006)); const h = g.size / 2;
    const r = mul(g.right, h); const u = mul(g.up, h);
    const index = ATLAS_LABELS.indexOf(g.label);
    const col = index % ATLAS_COLUMNS; const row = Math.floor(index / ATLAS_COLUMNS);
    const u0 = col / ATLAS_COLUMNS; const u1 = (col + 1) / ATLAS_COLUMNS;
    const vTop = 1 - row / ATLAS_COLUMNS; const vBottom = 1 - (row + 1) / ATLAS_COLUMNS;
    const corners = [[sub(sub(c, r), u), u0, vBottom], [sub(add(c, r), u), u1, vBottom], [add(add(c, r), u), u1, vTop],
      [sub(sub(c, r), u), u0, vBottom], [add(add(c, r), u), u1, vTop], [add(sub(c, r), u), u0, vTop]];
    for (const [p, uu, vv] of corners) { positions.push(...p); normals.push(...g.normal); uvs.push(uu, vv); }
  }
  return { positions: new Float32Array(positions), normals: new Float32Array(normals), uvs: new Float32Array(uvs) };
}
