#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vUv;
uniform sampler2D uPhoto;
uniform sampler2D uMask;
uniform sampler2D uPhotoB;
uniform sampler2D uMaskB;
uniform float uBlend;
uniform float uField;
uniform float uDry;
uniform float uHealth;
uniform float uTime;
uniform float uFlow;
uniform float uProgress;
uniform float uRain;
uniform float uWind;
uniform float uCloud;
uniform float uGold;
uniform float uHeat;
uniform float uLow;
uniform float uRippleAge;
uniform vec2 uRipple;

const float HORIZON = 0.42;
float second = 0.0;

vec3 photoAt(vec2 p) {
    return second > 0.5 ? texture2D(uPhotoB, p).rgb : texture2D(uPhoto, p).rgb;
}

vec3 maskAt(vec2 p) {
    return second > 0.5 ? texture2D(uMaskB, p).rgb : texture2D(uMask, p).rgb;
}

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec2(17.0, 9.0);
        amplitude *= 0.5;
    }
    return value;
}

vec2 waterCoord(vec2 uv) {
    float s = max(uv.y - HORIZON, 0.001) / (1.0 - HORIZON);
    float depth = 1.0 / (s + 0.10);
    return vec2((uv.x - 0.68) * depth * 1.8, depth * 1.6);
}

float waterHeight(vec2 uv) {
    vec2 p = waterCoord(uv);
    float t = uTime * (0.16 + 0.95 * uFlow);
    float broad = noise(vec2(p.x * 1.6, (p.y + t) * 1.1));
    float fine = noise(vec2(p.x * 3.7 + 3.0, (p.y + t * 1.6) * 2.7));
    return broad * 0.62 + fine * 0.38;
}

float rainRing(vec2 uv, float scale, float t) {
    vec2 p = uv * vec2(scale, scale * 2.3);
    vec2 id = floor(p);
    vec2 f = fract(p) - 0.5;
    float seed = hash(id);
    float phase = fract(t * (0.65 + seed * 0.5) + seed);
    vec2 center = (vec2(hash(id + 1.7), hash(id + 2.9)) - 0.5) * 0.5;
    float d = length(f - center);
    float radius = phase * 0.42;
    return sin(42.0 * (d - radius)) * (1.0 - phase) * smoothstep(radius + 0.05, radius, d);
}

void main() {
    vec2 uv = vUv;
    if (uBlend > 0.0005) {
        uv.x += uBlend;
        if (uv.x >= 1.0) {
            uv.x -= 1.0;
            second = 1.0;
        }
    }
    vec3 mask = maskAt(uv);
    float water = mask.r;
    float plants = mask.g;
    float sky = mask.b;
    float time = uTime;
    float s = clamp((uv.y - HORIZON) / (1.0 - HORIZON), 0.0, 1.0);
    vec2 sampleUv = uv;

    float haze = exp(-abs(uv.y - HORIZON) * 13.0);
    sampleUv.x += sin(uv.y * 260.0 + time * 5.2) * 0.0017 * uHeat * haze * (1.0 - water);

    float height = max(0.0, HORIZON - uv.y) / HORIZON;
    float swayAmount = (0.0007 + 0.0048 * uWind) * plants * (0.25 + height);
    float gust = 0.55 + 0.45 * sin(time * 0.7 + uv.x * 3.0);
    sampleUv.x += sin(time * (1.3 + uWind) + uv.x * 15.0 + uv.y * 24.0) * swayAmount * gust;
    sampleUv.y += cos(time * 1.1 + uv.x * 19.0) * swayAmount * 0.35;

    float fieldWave = 0.0;
    if (uField > 0.5) {
        float drift = noise(uv * vec2(2.6, 4.2) + vec2(time * 0.05, 0.0));
        fieldWave = sin(uv.x * 7.5 + uv.y * 5.0 - time * (1.0 + 2.4 * uWind) + drift * 5.0);
        float reach = plants * (0.0016 + 0.0075 * uWind) * (0.45 + 0.55 * smoothstep(0.0, 1.0, 1.0 - uv.y));
        sampleUv += vec2(fieldWave, fieldWave * 0.28) * reach;
    }

    vec2 normal = vec2(0.0);
    float ring = 0.0;
    if (water > 0.01) {
        float e = 0.0028;
        float h = waterHeight(uv);
        normal = vec2(waterHeight(uv + vec2(e, 0.0)) - h, waterHeight(uv + vec2(0.0, e)) - h) / e;
        normal *= 0.0075 + 0.0075 * uFlow;
        if (uRain > 0.0 && uLow < 0.5) {
            ring = rainRing(uv, 15.0, time) + rainRing(uv + 0.37, 24.0, time * 1.2);
            normal += vec2(ring) * 0.018 * uRain;
        }
        if (uRippleAge < 1.4) {
            vec2 delta = (uv - uRipple) * vec2(1.6, 2.2);
            float d = length(delta);
            float wave = sin(d * 95.0 - uRippleAge * 19.0) * exp(-d * 8.0) * (1.0 - uRippleAge / 1.4);
            normal += normalize(delta + 0.0001) * wave * 0.022;
        }
        vec2 moved = uv + normal * (0.016 + 0.05 * s) * water;
        if (maskAt(moved).r > 0.6) {
            sampleUv = moved;
        }
    }

    vec3 color = photoAt(sampleUv);
    if (uField > 0.5) {
        color *= 1.0 + plants * fieldWave * (0.022 + 0.062 * uWind);
    }
    float stress = clamp(uDry * 0.72 + (1.0 - uHealth) * 0.62, 0.0, 1.0);
    if (stress > 0.001) {
        float plantLuma = dot(color, vec3(0.299, 0.587, 0.114));
        vec3 straw = plantLuma * vec3(1.28, 1.06, 0.62);
        color = mix(color, straw, plants * stress);
    }
    if (uDry > 0.001) {
        float ground = clamp(1.0 - plants - sky - water, 0.0, 1.0);
        color = mix(color, color * vec3(1.14, 1.03, 0.84), ground * uDry);
        if (uLow < 0.5) {
            float crackA = smoothstep(0.022, 0.0, abs(noise(uv * vec2(36.0, 66.0)) - 0.5));
            float crackB = smoothstep(0.018, 0.0, abs(noise(uv * vec2(71.0, 122.0) + 4.0) - 0.5));
            color *= 1.0 - (crackA * 0.42 + crackB * 0.24) * ground * uDry;
        }
    }

    if (water > 0.01) {
        vec3 n = normalize(vec3(-normal * 3.2, 1.0));
        float fresnel = pow(1.0 - clamp(s, 0.0, 1.0), 2.4);
        vec3 skyTone = mix(vec3(0.70, 0.74, 0.74), vec3(0.98, 0.72, 0.42), uGold);
        vec3 surface = mix(color, skyTone, fresnel * 0.20);
        float glint = pow(max(dot(n, normalize(vec3(0.10, -0.34, 0.93))), 0.0), 190.0) * (0.25 + 0.75 * s);
        vec2 p = waterCoord(uv);
        float t = time * (0.16 + 0.95 * uFlow);
        float streak = smoothstep(0.58, 0.98, noise(vec2(p.x * 11.0, (p.y + t * 1.45) * 0.55)));
        surface += skyTone * (glint * (0.10 + 0.08 * uFlow) + streak * uFlow * 0.028);
        surface += vec3(ring) * 0.035 * uRain;
        color = mix(color, surface, water);
    }

    if (uProgress > 0.0 && uLow < 0.5 && water < 0.98) {
        float reach = 0.008 + 0.034 * uProgress;
        float near = max(max(maskAt(uv + vec2(reach, 0.0)).r, maskAt(uv - vec2(reach, 0.0)).r),
                         max(maskAt(uv + vec2(0.0, reach)).r, maskAt(uv - vec2(0.0, reach)).r));
        float wetBank = clamp(near - water, 0.0, 1.0) * (1.0 - sky) * smoothstep(0.0, 0.05, uProgress);
        color *= 1.0 - 0.30 * wetBank;
    }

    if (uCloud > 0.01) {
        float shadow = smoothstep(0.42, 0.74, noise(vec2(uv.x * 2.1 + time * (0.016 + 0.03 * uWind), uv.y * 3.0)));
        color *= 1.0 - shadow * uCloud * 0.09 * (1.0 - sky);
        if (sky > 0.01) {
            float cloudForm = smoothstep(0.34, 0.78, fbm(vec2(uv.x * 3.0 + time * (0.012 + 0.03 * uWind), uv.y * 6.0)));
            color = mix(color, mix(vec3(0.90, 0.91, 0.89), vec3(0.68, 0.71, 0.73), uRain), sky * cloudForm * uCloud * 0.62);
        }
    }

    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luma), 0.10 * uRain) * (1.0 - 0.05 * uRain);
    color = mix(color, color * vec3(1.13, 1.0, 0.82), uHeat * 0.55);

    vec3 goldLight = color * vec3(1.15, 0.98, 0.76);
    goldLight += sky * vec3(0.11, 0.04, -0.03) * (1.0 - uv.y / HORIZON * 0.25);
    color = mix(color, goldLight, uGold * 0.3);

    if (uRain > 0.0) {
        vec2 r = vec2(uv.x + uv.y * 0.11, uv.y);
        float rainA = smoothstep(0.965, 1.0, noise(vec2(r.x * 150.0, r.y * 5.0 - time * 8.5)));
        float rainB = uLow < 0.5 ? smoothstep(0.975, 1.0, noise(vec2(r.x * 230.0 + 7.0, r.y * 7.0 - time * 12.0))) : 0.0;
        color += vec3(0.62, 0.68, 0.72) * (rainA + rainB * 0.7) * uRain * 0.55;
    }

    vec2 corner = uv - 0.5;
    color *= 1.0 - 0.06 * dot(corner, corner);
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
