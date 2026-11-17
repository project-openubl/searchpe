// Emit load (DESIGN.md §25): documents per second through POST /documents, against a company in
// the "fake" environment (the SUNAT fake answers). Every request has its own Idempotency-Key.
//
//   k6 run -e URL=https://staging.example -e TOKEN=$TOKEN -e TENANT=... -e COMPANY=... \
//          -e RATE=85 -e DURATION=30m hack/load/emit.js
//
// RATE is documents/second (§25: ~83/s sustained, bursts of 5,000/minute).
import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const URL = __ENV.URL || 'http://localhost:8080';
const base = `${URL}/api/v1/tenants/${__ENV.TENANT}/companies/${__ENV.COMPANY}/documents`;

export const options = {
  scenarios: {
    emit: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 500),
    },
  },
  thresholds: {
    // Emit is local (engine + HSM + database + storage): fast and reliable whatever SUNAT does
    'http_req_duration{name:emit}': ['p(99)<1500'],
    'checks{name:emit}': ['rate>0.999'],
  },
};

const body = JSON.stringify({
  document: {
    kind: 'Invoice',
    spec: {
      cliente: { tipo_documento_identidad: '6', numero_documento_identidad: '20111111111', nombre: 'CLIENTE DE CARGA SAC' },
      detalles: [{ descripcion: 'Producto', cantidad: 2, precio: 50, igv_tipo: '10' }],
    },
  },
});

export default function () {
  const res = http.post(base, body, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${__ENV.TOKEN}`, 'Idempotency-Key': uuidv4() },
    tags: { name: 'emit' },
  });
  check(res, { created: (r) => r.status === 201 }, { name: 'emit' });
}
