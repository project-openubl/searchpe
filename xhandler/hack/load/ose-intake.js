// OSE intake load (DESIGN.md §11.4, §18): sendBill over SOAP at SUNAT's path, with pre-signed
// documents made by make-ose-docs.sh (each number is sent once; SUNAT's rules forbid reuse).
//
//   ./hack/load/make-ose-docs.sh 20123456789 5000 /tmp/ose-docs
// Prerequisites: an OSE user for the RUC (POST /api/admin/v1/ose/clients) and the RUC in the
// association list (PUT /api/admin/v1/ose/association).
//
//   k6 run -e URL=... -e OSE_USER=... -e OSE_PASSWORD=... -e DOCS=/tmp/ose-docs -e COUNT=5000 \
//          -e VUS=50 hack/load/ose-intake.js
//
// Then watch forwarding: xhandler_ose_forward_oldest_seconds must stay well under 3600.
import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const URL = __ENV.URL || 'http://localhost:8080';
const COUNT = Number(__ENV.COUNT || 100);
const names = open(`${__ENV.DOCS}/index.txt`).split('\n').filter((l) => l.length > 0).slice(0, COUNT);
const zips = names.map((n) => encoding.b64encode(open(`${__ENV.DOCS}/${n}.zip`, 'b')));

export const options = {
  scenarios: {
    intake: {
      executor: 'shared-iterations',
      iterations: names.length,
      vus: Number(__ENV.VUS || 50),
      maxDuration: __ENV.DURATION || '30m',
    },
  },
  thresholds: {
    'http_req_duration{name:sendBill}': ['p(99)<2000'],
    'checks{name:sendBill}': ['rate>0.9996'],
  },
};

export default function () {
  const i = exec.scenario.iterationInTest;
  const envelope = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"><soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>${__ENV.OSE_USER}</wsse:Username><wsse:Password>${__ENV.OSE_PASSWORD}</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header><soapenv:Body><ser:sendBill><fileName>${names[i]}.zip</fileName><contentFile>${zips[i]}</contentFile></ser:sendBill></soapenv:Body></soapenv:Envelope>`;
  const res = http.post(`${URL}/ol-ti-itcpfegem/billService`, envelope, {
    headers: { 'Content-Type': 'text/xml' },
    tags: { name: 'sendBill' },
  });
  check(res, { cdr: (r) => r.status === 200 && r.body.includes('applicationResponse') }, { name: 'sendBill' });
}
