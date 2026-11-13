import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { ATLAS_COLUMNS, ATLAS_LABELS, dieGeometry, glyphQuads, roundedBody } from '../dice/geometry.js';
import { bakeThrow, poseAt } from '../dice/physics.js';

// The same ten-style palette as the Android presets (DiceStylePresets.kt).
const STYLES = [
  { sides: 20, color: 0x2563eb, ink: 0xf6c453, kind: 'resin' },
  { sides: 12, color: 0x8547d4, ink: 0x65e7de, kind: 'gem' },
  { sides: 10, color: 0x20232b, ink: 0xe3af51, kind: 'matte' },
  { sides: 8, color: 0x0c9d75, ink: 0xe1d18a, kind: 'gem' },
  { sides: 6, color: 0xba3447, ink: 0xf59b42, kind: 'metal' },
  { sides: 4, color: 0xf2e9e2, ink: 0x8261ad, kind: 'resin' },
];
const HALF_WIDTH = 4.2;
const HALF_HEIGHT = 2.6;
const SCALE = 0.86;

function makeAtlas() {
  const size = 1024;
  const canvas = document.createElement('canvas');
  canvas.width = size; canvas.height = size;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, size, size);
  ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
  const cell = size / ATLAS_COLUMNS;
  ATLAS_LABELS.forEach((label, index) => {
    const cx = (index % ATLAS_COLUMNS + 0.5) * cell;
    const cy = (Math.floor(index / ATLAS_COLUMNS) + 0.5) * cell;
    const underline = label === '6' || label === '9';
    let fontSize = cell;
    ctx.font = `900 ${fontSize}px Cinzel, Georgia, serif`;
    let m = ctx.measureText(label);
    const height = m.actualBoundingBoxAscent + m.actualBoundingBoxDescent;
    const maxW = cell * (label.length > 1 ? 0.84 : 0.7);
    const maxH = cell * (underline ? 0.6 : label.length > 1 ? 0.58 : 0.7);
    fontSize *= Math.min(maxW / m.width, maxH / height);
    ctx.font = `900 ${fontSize}px Cinzel, Georgia, serif`;
    m = ctx.measureText(label);
    const shift = underline ? -cell * 0.045 : 0;
    const baseline = cy + (m.actualBoundingBoxAscent - m.actualBoundingBoxDescent) / 2 + shift;
    ctx.fillText(label, cx, baseline);
    if (underline) {
      const w = m.width * 0.78;
      ctx.fillRect(cx - w / 2, baseline + cell * 0.055, w, cell * 0.06);
    }
  });
  const texture = new THREE.CanvasTexture(canvas);
  texture.anisotropy = 8;
  texture.colorSpace = THREE.NoColorSpace;
  return texture;
}

function feltTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 1024; canvas.height = 640;
  const ctx = canvas.getContext('2d');
  const g = ctx.createRadialGradient(470, 290, 40, 512, 320, 620);
  g.addColorStop(0, '#1f2a4a'); g.addColorStop(0.55, '#141a33'); g.addColorStop(1, '#080a16');
  ctx.fillStyle = g; ctx.fillRect(0, 0, 1024, 640);
  const image = ctx.getImageData(0, 0, 1024, 640);
  let seed = 7;
  const rand = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  for (let i = 0; i < image.data.length; i += 4) {
    const n = (rand() - 0.5) * 14;
    image.data[i] += n; image.data[i + 1] += n; image.data[i + 2] += n;
  }
  ctx.putImageData(image, 0, 0);
  ctx.strokeStyle = 'rgba(232,193,112,0.16)'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.arc(512, 320, 230, 0, Math.PI * 2); ctx.stroke();
  ctx.strokeStyle = 'rgba(183,168,255,0.10)';
  ctx.beginPath(); ctx.arc(512, 320, 250, 0, Math.PI * 2); ctx.stroke();
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function bodyMaterial(style) {
  const common = { color: style.color, envMapIntensity: 1.1 };
  if (style.kind === 'metal') return new THREE.MeshPhysicalMaterial({ ...common, metalness: 1, roughness: 0.24 });
  if (style.kind === 'matte') return new THREE.MeshPhysicalMaterial({ ...common, roughness: 0.7, clearcoat: 0.1 });
  if (style.kind === 'gem') {
    return new THREE.MeshPhysicalMaterial({
      ...common, roughness: 0.06, transmission: 0.62, thickness: 1.1, ior: 1.6,
      attenuationColor: new THREE.Color(style.color), attenuationDistance: 0.9, clearcoat: 1, clearcoatRoughness: 0.03,
    });
  }
  return new THREE.MeshPhysicalMaterial({ ...common, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.06, sheen: 0.25 });
}

function dieMesh(style, atlas) {
  const geometry = dieGeometry(style.sides);
  const body = roundedBody(geometry);
  const bodyGeometry = new THREE.BufferGeometry();
  bodyGeometry.setAttribute('position', new THREE.BufferAttribute(body.positions, 3));
  bodyGeometry.setAttribute('normal', new THREE.BufferAttribute(body.normals, 3));
  const glyphs = glyphQuads(geometry);
  const glyphGeometry = new THREE.BufferGeometry();
  glyphGeometry.setAttribute('position', new THREE.BufferAttribute(glyphs.positions, 3));
  glyphGeometry.setAttribute('normal', new THREE.BufferAttribute(glyphs.normals, 3));
  glyphGeometry.setAttribute('uv', new THREE.BufferAttribute(glyphs.uvs, 2));
  const group = new THREE.Group();
  const bodyMesh = new THREE.Mesh(bodyGeometry, bodyMaterial(style));
  bodyMesh.castShadow = true;
  const ink = new THREE.MeshStandardMaterial({
    color: style.ink, alphaMap: atlas, transparent: true, depthWrite: false, roughness: 0.35, metalness: 0.2,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2,
  });
  group.add(bodyMesh, new THREE.Mesh(glyphGeometry, ink));
  group.scale.setScalar(SCALE);
  return group;
}

/** Interactive WebGL dice tray running the same physics as the Android app. */
export default function DiceHero({ rollKey, onResults, label }) {
  const host = useRef(null);
  const sceneRef = useRef(null);

  useEffect(() => {
    const element = host.current;
    if (!element) return undefined;
    let renderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    } catch {
      element.dataset.fallback = 'true';
      return undefined;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFShadowMap;
    element.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const pmrem = new THREE.PMREMGenerator(renderer);
    const environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    scene.environment = environment;

    const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 100);
    camera.up.set(0, 0, 1);
    camera.position.set(0, -9.0, 9.4);
    camera.lookAt(0, -0.15, 0);

    const felt = feltTexture();
    const table = new THREE.Mesh(
      new THREE.PlaneGeometry(HALF_WIDTH * 2, HALF_HEIGHT * 2),
      new THREE.MeshStandardMaterial({ map: felt, roughness: 0.95, metalness: 0 }),
    );
    table.receiveShadow = true;
    scene.add(table);

    // Leather rails with a gilded inner edge frame the tray.
    const railMaterial = new THREE.MeshPhysicalMaterial({ color: 0x241712, roughness: 0.55, clearcoat: 0.35 });
    const trimMaterial = new THREE.MeshPhysicalMaterial({ color: 0xe8c170, metalness: 1, roughness: 0.25 });
    const rail = 0.42; const railHeight = 0.55;
    [[0, HALF_HEIGHT + rail / 2, HALF_WIDTH * 2 + rail * 2, rail], [0, -HALF_HEIGHT - rail / 2, HALF_WIDTH * 2 + rail * 2, rail],
      [HALF_WIDTH + rail / 2, 0, rail, HALF_HEIGHT * 2], [-HALF_WIDTH - rail / 2, 0, rail, HALF_HEIGHT * 2]]
      .forEach(([x, y, w, h]) => {
        const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, railHeight), railMaterial);
        mesh.position.set(x, y, railHeight / 2 - 0.05);
        mesh.castShadow = true; mesh.receiveShadow = true;
        scene.add(mesh);
        const trim = new THREE.Mesh(new THREE.BoxGeometry(w + 0.02, h + 0.02, 0.035), trimMaterial);
        trim.position.set(x, y, railHeight - 0.03);
        scene.add(trim);
      });

    const key = new THREE.DirectionalLight(0xfff1dc, 2.4);
    key.position.set(-4.5, -3.5, 9);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    key.shadow.camera.left = -6; key.shadow.camera.right = 6;
    key.shadow.camera.top = 4.5; key.shadow.camera.bottom = -4.5;
    key.shadow.radius = 6;
    key.shadow.bias = -0.0004;
    scene.add(key);
    scene.add(new THREE.HemisphereLight(0x9aa4ff, 0x1a1208, 0.55));
    const rim = new THREE.DirectionalLight(0xb7a8ff, 0.8);
    rim.position.set(5, 4, 3);
    scene.add(rim);

    const atlas = makeAtlas();
    document.fonts?.load('900 64px Cinzel').then(() => {
      const fresh = makeAtlas();
      sceneRef.current?.dice.forEach((die) => { die.children[1].material.alphaMap = fresh.clone(); die.children[1].material.alphaMap.needsUpdate = true; die.children[1].material.needsUpdate = true; });
      requestRender();
    }).catch(() => {});
    const dice = STYLES.map((style) => { const mesh = dieMesh(style, atlas); scene.add(mesh); return mesh; });

    let frame = 0;
    let animation = null;
    function render() {
      frame = 0;
      if (animation) {
        const t = (performance.now() - animation.start) / 1000;
        dice.forEach((die, i) => {
          const { position, quaternion } = poseAt(animation.baked, i, t);
          die.position.set(position[0], position[1], position[2]);
          die.quaternion.set(quaternion[1], quaternion[2], quaternion[3], quaternion[0]);
        });
        if (t < animation.baked.duration) frame = requestAnimationFrame(render);
        else if (!animation.reported) { animation.reported = true; onResults?.(animation.baked.results.map((value, i) => ({ sides: STYLES[i].sides, value }))); }
      }
      renderer.render(scene, camera);
    }
    function requestRender() { if (!frame) frame = requestAnimationFrame(render); }

    function resize() {
      const width = element.clientWidth || 1; const height = element.clientHeight || 1;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      // Fit the whole tray (rails included) horizontally, never cropping it vertically.
      const distance = camera.position.length();
      const horizontal = 2 * Math.atan((HALF_WIDTH + 0.62) * 1.06 / distance);
      const fitted = 2 * Math.atan(Math.tan(horizontal / 2) / camera.aspect) * 180 / Math.PI;
      camera.fov = Math.max(29, fitted);
      camera.updateProjectionMatrix();
      requestRender();
    }
    const observer = new ResizeObserver(resize);
    observer.observe(element);
    resize();

    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    sceneRef.current = {
      dice,
      roll(seed) {
        const baked = bakeThrow(STYLES.map((s) => s.sides), { seed, scale: SCALE, halfWidth: HALF_WIDTH, halfHeight: HALF_HEIGHT });
        animation = { baked, start: performance.now() - (reduceMotion ? baked.duration * 1000 : 0), reported: false };
        requestRender();
      },
    };
    sceneRef.current.roll((Math.random() * 2 ** 31) | 0);

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      scene.traverse((object) => {
        object.geometry?.dispose();
        const materials = Array.isArray(object.material) ? object.material : [object.material];
        materials.forEach((m) => { m?.alphaMap?.dispose(); m?.map?.dispose(); m?.dispose(); });
      });
      environment.dispose();
      pmrem.dispose();
      atlas.dispose();
      renderer.dispose();
      renderer.domElement.remove();
      sceneRef.current = null;
    };
    // The scene is created once; throws are requested through rollKey.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (rollKey > 0) sceneRef.current?.roll((Math.random() * 2 ** 31) | 0);
  }, [rollKey]);

  return <div className="dice-hero-canvas" ref={host} role="img" aria-label={label} />;
}
