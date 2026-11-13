package com.keyserdsoze.dicethrower.ui.dice3d

/**
 * GLSL ES 1.00 programs for the dice table. World space: the felt is the XY plane, +Z points
 * up towards the camera, so a die face "on top" has a world normal close to +Z.
 */
internal object DiceShaders {
    const val DICE_VERTEX = """
        uniform mat4 uMvp;
        uniform mat4 uModel;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aUv;
        varying vec3 vWorld;
        varying vec3 vNormal;
        varying vec3 vLocal;
        varying vec2 vUv;

        void main() {
            vec4 world = uModel * vec4(aPosition, 1.0);
            vWorld = world.xyz;
            vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
            vLocal = aPosition;
            vUv = aUv;
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """

    const val DICE_FRAGMENT = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif

        uniform vec3 uCamera;
        uniform vec3 uBase;
        uniform vec3 uAccent;
        uniform vec3 uInk;
        uniform vec3 uFloor;
        // roughness, metalness, clearcoat, translucency
        uniform vec4 uSurface;
        // swirl, inner glow, sparkle, per-die seed
        uniform vec4 uDetail;
        // additive emissive rgb, w = brightness
        uniform vec4 uTint;
        // x = felt height (world z), y = die size
        uniform vec2 uGround;
        // die centre (world); w = normal curvature (faces read as subtly domed)
        uniform vec4 uCenter;
        uniform float uInkPass;
        uniform sampler2D uGlyphs;

        varying vec3 vWorld;
        varying vec3 vNormal;
        varying vec3 vLocal;
        varying vec2 vUv;

        const vec3 KEY = vec3(-0.4236, 0.5295, 0.7353);
        const vec3 FILL = vec3(0.6402, -0.4268, 0.6388);
        const vec3 KEY_COLOR = vec3(1.00, 0.95, 0.88);
        const vec3 FILL_COLOR = vec3(0.55, 0.64, 0.86);

        vec3 environment(vec3 r) {
            float up = r.z;
            vec3 horizon = vec3(0.045, 0.048, 0.06);
            vec3 zenith = vec3(0.34, 0.36, 0.40);
            vec3 sky = mix(horizon, zenith, smoothstep(0.0, 1.0, up));
            vec3 ground = uFloor * 0.30;
            vec3 base = mix(ground, sky, smoothstep(-0.25, 0.08, up));
            float softbox = smoothstep(0.74, 0.96, dot(r, KEY));
            float strip = smoothstep(0.84, 0.98, dot(r, FILL));
            float window = smoothstep(0.92, 0.995, dot(r, vec3(0.0, 0.8, 0.6))) * 0.5;
            return base + KEY_COLOR * softbox * 2.6 + FILL_COLOR * strip * 1.2 + vec3(0.9, 0.92, 1.0) * window;
        }

        float ggx(float nh, float roughness) {
            float a = max(roughness * roughness, 0.0025);
            float a2 = a * a;
            float d = nh * nh * (a2 - 1.0) + 1.0;
            return a2 / (3.14159 * d * d);
        }

        float marble(vec3 p) {
            p = p * 2.3 + uDetail.w;
            float n = sin(p.x * 1.7 + sin(p.y * 2.3 + sin(p.z * 1.9 + p.x * 0.7) * 1.4) * 1.6);
            n += 0.55 * sin(p.y * 3.3 + sin(p.z * 2.9 + p.x * 1.3) * 1.8 + 1.7);
            float band = abs(n) / 1.55;
            // Two-colour poured resin: a thin vein of pure second pigment with a faint halo.
            float core = 1.0 - smoothstep(0.012, 0.05, band);
            float halo = 1.0 - smoothstep(0.05, 0.26, band);
            return clamp(core * 0.95 + halo * 0.16, 0.0, 1.0);
        }

        vec3 aces(vec3 x) {
            return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
        }

        vec3 shade(vec3 albedo, vec3 n, vec3 v, float roughness, float metal, float clearcoat, float translucency) {
            float nv = max(dot(n, v), 0.0);
            vec3 f0 = mix(vec3(0.04), albedo, metal);
            vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - nv, 5.0);

            float wrap = 0.25 * translucency;
            float keyDiffuse = max((dot(n, KEY) + wrap) / (1.0 + wrap), 0.0);
            float fillDiffuse = max((dot(n, FILL) + wrap) / (1.0 + wrap), 0.0);
            vec3 hemisphere = mix(uFloor * 0.22 + vec3(0.012), vec3(0.15, 0.16, 0.19), n.z * 0.5 + 0.5);
            vec3 diffuseLight = KEY_COLOR * keyDiffuse * 1.95 + FILL_COLOR * fillDiffuse * 0.36 + hemisphere;
            vec3 diffuse = albedo * diffuseLight * (1.0 - metal);

            vec3 hk = normalize(KEY + v);
            vec3 hf = normalize(FILL + v);
            float specularKey = ggx(max(dot(n, hk), 0.0), roughness) * max(dot(n, KEY), 0.0);
            float specularFill = ggx(max(dot(n, hf), 0.0), roughness) * max(dot(n, FILL), 0.0);
            vec3 specular = fresnel * (KEY_COLOR * specularKey * 1.4 + FILL_COLOR * specularFill * 0.35);

            vec3 reflected = reflect(-v, n);
            vec3 sharpEnv = environment(reflected);
            vec3 blurredEnv = mix(environment(normalize(mix(reflected, n, 0.55))), hemisphere * 1.4, 0.35);
            vec3 env = mix(sharpEnv, blurredEnv, clamp(roughness * 1.25, 0.0, 1.0));
            // Fine brushing on metal: anisotropic streaks catch the light as the die turns.
            float brushed = 0.80 + 0.20 * sin(vLocal.y * 260.0 + sin(vLocal.x * 9.0 + vLocal.z * 7.0) * 3.0);
            vec3 reflection = env * fresnel * (1.0 - roughness * 0.55) * mix(1.0, brushed, metal * 0.7);

            float coatFresnel = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
            vec3 coat = clearcoat * coatFresnel * (environment(reflected) * 0.75 +
                KEY_COLOR * ggx(max(dot(n, hk), 0.0), 0.12) * max(dot(n, KEY), 0.0) * 0.22);

            vec3 scatter = albedo * translucency * (0.04 + 0.34 * pow(1.0 - nv, 2.0)) * (0.5 + 0.5 * keyDiffuse);
            return diffuse + specular + reflection + coat + scatter;
        }

        void main() {
            vec3 geometric = normalize(vNormal);
            // Real dice faces are never perfectly flat: a hint of doming makes reflections travel
            // across each face instead of filling it with one flat colour.
            vec3 n = normalize(mix(geometric, normalize(vWorld - uCenter.xyz), uCenter.w));
            vec3 v = normalize(uCamera - vWorld);
            float brightness = uTint.w;

            if (uInkPass > 0.5) {
                float a = texture2D(uGlyphs, vUv).a;
                float groove = texture2D(uGlyphs, vUv + vec2(-0.0028, 0.0028)).a;
                float alpha = max(a, groove * 0.62);
                if (alpha < 0.01) discard;
                vec3 ink = shade(uInk, n, v, 0.38, 0.0, 0.35, 0.0) * mix(0.18, 1.0, a / max(alpha, 0.001));
                float inkLift = clamp((vWorld.z - uGround.x) / uGround.y, 0.0, 1.0);
                ink *= mix(0.42, 1.0, smoothstep(0.0, 0.95, inkLift));
                vec3 color = aces((ink * brightness + uTint.rgb * 0.5) * 1.05);
                gl_FragColor = vec4(pow(color, vec3(0.4545)), alpha);
                return;
            }

            vec3 albedo = uBase;
            if (uDetail.x > 0.0) albedo = mix(albedo, uAccent, uDetail.x * marble(vLocal));
            vec3 color = shade(albedo, n, v, uSurface.x, uSurface.y, uSurface.z, uSurface.w);
            // Contact occlusion: the part of the die close to the felt receives less light.
            float lift = clamp((vWorld.z - uGround.x) / uGround.y, 0.0, 1.0);
            color *= mix(0.42, 1.0, smoothstep(0.0, 0.95, lift));

            if (uDetail.y > 0.0) {
                float nv = max(dot(n, v), 0.0);
                // Gemstone: light trapped in the crystal, strongest through the face centres.
                float core = pow(nv, 1.6) * (0.55 + 0.45 * marble(vLocal * 0.6 + 3.1));
                color += uAccent * uDetail.y * (0.18 + 0.55 * core);
                vec3 refracted = refract(-v, n, 0.66);
                color += uBase * environment(refracted) * 0.45 * uDetail.y;
            }
            if (uDetail.z > 0.0) {
                vec3 facet = floor(vLocal * 9.0 + 0.5);
                float hash = fract(sin(dot(facet, vec3(12.9898, 78.233, 37.719)) + uDetail.w) * 43758.5453);
                float glint = pow(max(dot(reflect(-v, n), KEY), 0.0), 48.0) * step(0.82, hash);
                color += KEY_COLOR * glint * 2.2 * uDetail.z;
            }

            color = color * brightness + uTint.rgb;
            gl_FragColor = vec4(pow(aces(color * 1.08), vec3(0.4545)), 1.0);
        }
    """

    const val TABLE_VERTEX = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        attribute vec2 aUv;
        varying vec2 vUv;
        varying vec2 vPlane;

        void main() {
            vUv = aUv;
            vPlane = aPosition.xy;
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """

    const val TABLE_FRAGMENT = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uUseTexture;
        uniform vec3 uFeltDark;
        uniform vec3 uFeltLight;
        uniform vec2 uHalf;
        varying vec2 vUv;
        varying vec2 vPlane;

        void main() {
            vec2 p = vPlane / uHalf;
            float d = length(p * vec2(1.0, 0.82));
            vec3 base;
            if (uUseTexture > 0.5) {
                base = texture2D(uTexture, vUv).rgb;
            } else {
                base = mix(uFeltLight, uFeltDark, smoothstep(0.0, 1.15, d));
            }
            // A warm lamp above the table: brighter play area, softly darker rails.
            float lamp = mix(1.07, 0.74, smoothstep(0.30, 1.35, length(p - vec2(-0.08, 0.10))));
            // Fine felt/stone grain restores micro-detail on upscaled artwork.
            vec2 cell = floor(vPlane * 140.0);
            float grain = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
            gl_FragColor = vec4(base * lamp * (1.0 + grain * 0.07), 1.0);
        }
    """

    const val SHADOW_VERTEX = """
        uniform mat4 uViewProjection;
        uniform vec3 uCenter;
        uniform vec2 uRadius;
        attribute vec2 aCorner;
        varying vec2 vCorner;

        void main() {
            vCorner = aCorner;
            gl_Position = uViewProjection * vec4(uCenter + vec3(aCorner * uRadius, 0.0), 1.0);
        }
    """

    const val SHADOW_FRAGMENT = """
        precision mediump float;
        uniform float uOpacity;
        uniform float uCore;
        varying vec2 vCorner;

        void main() {
            float d = length(vCorner);
            float soft = 1.0 - smoothstep(0.0, 1.0, d);
            float core = 1.0 - smoothstep(0.0, 0.56, d);
            float alpha = uOpacity * soft * soft + uCore * core * core;
            gl_FragColor = vec4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 0.92));
        }
    """
}
