import test from 'node:test';
import assert from 'node:assert/strict';
import { bakeThrow, poseAt, rotateVector } from './physics.js';
import { dieGeometry, roundedBody, glyphQuads, ATLAS_LABELS } from './geometry.js';

const SIDES = [4, 6, 8, 10, 12, 20, 100];

test('every die geometry yields a rounded body, numerals and valid atlas labels', () => {
  for (const sides of SIDES) {
    const g = dieGeometry(sides);
    const body = roundedBody(g);
    assert.ok(body.positions.length > g.faces.length * 9, `d${sides} body`);
    assert.equal(body.positions.length, body.normals.length);
    const quads = glyphQuads(g);
    assert.equal(quads.positions.length / 3, g.glyphs.length * 6);
    for (const anchor of g.anchors) assert.ok(ATLAS_LABELS.includes(anchor.label), anchor.label);
  }
});

test('opposite faces add up like real dice', () => {
  for (const [sides, total] of [[6, 7], [8, 9], [10, 11], [12, 13], [20, 21]]) {
    const g = dieGeometry(sides);
    for (const a of g.anchors) {
      const opposite = g.anchors.reduce((best, b) => (b.dir[0] * a.dir[0] + b.dir[1] * a.dir[1] + b.dir[2] * a.dir[2]
        < best.dir[0] * a.dir[0] + best.dir[1] * a.dir[1] + best.dir[2] * a.dir[2] ? b : best));
      assert.equal(Number(a.label) + Number(opposite.label), total);
    }
  }
});

test('throws are deterministic, bounded, flat and readable', () => {
  const halfWidth = 4.2; const halfHeight = 2.6;
  for (let seed = 1; seed <= 25; seed++) {
    const dice = [20, 12, 10, 8, 6, 4];
    const a = bakeThrow(dice, { seed, halfWidth, halfHeight });
    const b = bakeThrow(dice, { seed, halfWidth, halfHeight });
    assert.deepEqual(a.results, b.results);
    assert.ok(a.duration > 0.8 && a.duration < 4, `duration ${a.duration}`);
    for (let t = 0; t <= a.duration; t += 1 / 20) {
      dice.forEach((sides, i) => {
        const { position, quaternion } = poseAt(a, i, t);
        for (const v of dieGeometry(sides).vertices) {
          const w = rotateVector(quaternion, v).map((x, k) => x * a.scale + position[k]);
          assert.ok(Math.abs(w[0]) <= halfWidth + 0.05 && Math.abs(w[1]) <= halfHeight + 0.05 && w[2] > -0.05, `seed ${seed} die ${i} t ${t}`);
        }
      });
    }
    a.results.forEach((label, i) => assert.ok(dieGeometry(dice[i]).anchors.some((x) => x.label === label)));
    const finals = dice.map((_, i) => poseAt(a, i, a.duration).position);
    finals.forEach((p, i) => finals.slice(i + 1).forEach((q, k) => {
      const j = i + 1 + k;
      const min = (dieGeometry(dice[i]).inradius + dieGeometry(dice[j]).inradius) * a.scale * 0.9;
      assert.ok(Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]) >= min, `seed ${seed}: dice ${i}/${j} overlap`);
    }));
  }
});
