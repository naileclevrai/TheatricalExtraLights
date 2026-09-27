#version 150

// Panache CO2 : raymarch d'un cone de brouillard dense et blanc, ombre de gris sur son flanc non
// eclaire, front dechiquete par le bruit, coupure de vanne qui remonte le jet. Rendu premultiplie
// (ONE, ONE_MINUS_SRC_ALPHA) : la fumee couvre ce qui est derriere elle, ce n'est pas de la lumiere.

uniform sampler2D Sampler1;   // profondeur de la scene

uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform int StepCount;
uniform float Time;

uniform vec3 Nozzle;        // bouche de la buse, espace vue
uniform vec3 Axis;          // axe du jet, espace vue, unitaire
uniform vec3 SideU;         // base fixe dans le monde, perpendiculaire a l'axe, espace vue
uniform vec3 SideV;
uniform vec3 UpV;           // verticale du monde en espace vue
uniform float PlumeLength;  // distance du front depuis la buse, blocs
uniform float CutFront;     // front de coupure depuis la buse, blocs ; negatif = vanne ouverte
uniform float Pressure;     // 0..1, intensite DMX
uniform float Dissipate;    // 0 vanne ouverte .. 1 nuage dissipe : le gaz lache s'etale et se dilue
uniform float Scroll;       // defilement du bruit le long du jet, blocs
uniform float NozzleRadius; // rayon a la bouche
uniform float FlashRadius;  // rayon apres la detente eclair
uniform float ConeTan;      // tangente du demi-angle du cone
uniform float Brightness;

in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(
        mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
        mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
        f.z
    );
}

// Trois octaves : volutes larges et grain fin par-dessus.
float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * vnoise(p);
        p = p * 2.17 + vec3(11.3, 7.1, 5.9);
        amp *= 0.5;
    }
    return sum;
}

float ign(vec2 px) {
    return fract(52.9829189 * fract(0.06711056 * px.x + 0.00583715 * px.y));
}

vec3 reconstructViewPos(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    return view.xyz / max(view.w, 1.0e-6);
}

// Rayon du panache a la distance z : detente eclair sur le premier demi-bloc, cone, tete qui gonfle.
float plumeRadius(float z) {
    float flash = NozzleRadius + (FlashRadius - NozzleRadius) * smoothstep(0.0, 0.6, z);
    float head = 1.0 + 0.7 * smoothstep(PlumeLength - 2.5, PlumeLength, z);
    return (flash + z * ConeTan) * head;
}

// Cylindre fini autour de l'axe, de zMin a zMax : borne de la marche.
bool intersectCylinder(vec3 ro, vec3 rd, float radius, float zMin, float zMax, out float t0, out float t1) {
    vec3 o = ro - Nozzle;
    float oz = dot(o, Axis);
    float dz = dot(rd, Axis);
    vec3 op = o - Axis * oz;
    vec3 dp = rd - Axis * dz;
    float a = dot(dp, dp);
    float b = 2.0 * dot(op, dp);
    float c = dot(op, op) - radius * radius;
    float tc0;
    float tc1;
    if (a < 1.0e-6) {
        if (c > 0.0) return false;
        tc0 = -1.0e9;
        tc1 = 1.0e9;
    } else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return false;
        float s = sqrt(disc);
        tc0 = (-b - s) / (2.0 * a);
        tc1 = (-b + s) / (2.0 * a);
    }
    float tz0;
    float tz1;
    if (abs(dz) < 1.0e-6) {
        if (oz < zMin || oz > zMax) return false;
        tz0 = -1.0e9;
        tz1 = 1.0e9;
    } else {
        tz0 = (zMin - oz) / dz;
        tz1 = (zMax - oz) / dz;
        if (tz0 > tz1) {
            float tmp = tz0;
            tz0 = tz1;
            tz1 = tmp;
        }
    }
    t0 = max(max(tc0, tz0), 0.0);
    t1 = min(tc1, tz1);
    return t1 > t0;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float sceneT = length(reconstructViewPos(uv, texture(Sampler1, uv).r));
    vec3 rd = normalize(reconstructViewPos(uv, 1.0));

    float len = max(PlumeLength, 0.05);
    // Nuage lache : il s'etale (rayon), se dilue (densite) et s'effiloche (bord).
    float spread = 1.0 + 0.9 * Dissipate;
    float rMax = plumeRadius(len) * spread * 1.3 + 0.25;
    float t0;
    float t1;
    if (!intersectCylinder(vec3(0.0), rd, rMax, -0.15, len + 1.0, t0, t1)) {
        discard;
    }
    t1 = min(t1, sceneT - 0.02);
    if (t1 <= t0) {
        discard;
    }

    int steps = clamp(StepCount, 12, 48);
    float dt = (t1 - t0) / float(steps);
    float t = t0 + dt * ign(gl_FragCoord.xy);

    // Lumiere de scene : depuis la camera, relevee vers le haut.
    vec3 lightDir = normalize(-rd + UpV * 0.8);
    float sigma = 9.0 * (0.6 + 0.4 * Pressure) * (1.0 - 0.9 * Dissipate);
    vec3 tint = vec3(0.97, 0.985, 1.0) * Brightness;

    vec3 accum = vec3(0.0);
    float alpha = 0.0;
    for (int i = 0; i < 48; i++) {
        if (i >= steps || alpha > 0.985) {
            break;
        }
        vec3 p = rd * t;
        vec3 o = p - Nozzle;
        float z = dot(o, Axis);
        vec3 perp = o - Axis * z;
        float r = length(perp);
        if (z > -0.1 && z < len + 1.0) {
            float zc = max(z, 0.0);
            float radius = plumeRadius(zc) * spread;
            float rho = r / max(radius, 1.0e-3);
            if (rho < 1.6) {
                // Bruit dans le repere du jet, fixe dans le monde, qui defile avec le gaz.
                vec3 q = vec3(dot(perp, SideU), dot(perp, SideV), z - Scroll);
                float n = fbm(q * 1.5);
                float n2 = vnoise(q * 5.0 + vec3(3.1, 7.7, -Time * 0.35));
                // Bord erode par les volutes.
                float edge = 1.0 - rho + (n - 0.5) * (0.9 + 0.6 * Dissipate) + (n2 - 0.5) * 0.3 - 0.45 * Dissipate;
                float d = smoothstep(0.0, 0.45, edge);
                // Front dechiquete, dilution vers la tete.
                float front = len + (n - 0.5) * 2.5;
                d *= 1.0 - smoothstep(front - 1.8, front + 0.3, z);
                d *= mix(1.0, 0.35, smoothstep(0.45 * len, len, z));
                // Coupure de vanne : derriere le front de coupure il n'y a plus de gaz.
                if (CutFront > 0.0) {
                    d *= smoothstep(CutFront - 0.5, CutFront + 1.0, z + (n - 0.5) * 1.2);
                }
                if (d > 0.001) {
                    vec3 nrm = r > 1.0e-4 ? perp / r : vec3(0.0);
                    float lit = 0.5 + 0.5 * dot(nrm, lightDir);
                    float shade = (0.55 + 0.45 * lit) * (0.9 + 0.1 * (1.0 - rho)) * (0.88 + 0.35 * (n - 0.5));
                    float a = 1.0 - exp(-sigma * d * dt);
                    float w = a * (1.0 - alpha);
                    accum += tint * shade * w;
                    alpha += w;
                }
            }
        }
        t += dt;
    }
    if (alpha < 0.003) {
        discard;
    }
    fragColor = vec4(accum, alpha);
}
