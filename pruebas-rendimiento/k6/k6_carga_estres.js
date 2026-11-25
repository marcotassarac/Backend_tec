import http from "k6/http";
import { check, sleep } from "k6";

// Prueba de carga y estres contra el Gestor de Inventario real (product-service y
// report-service), autenticado con un JWT real obtenido de auth-service.

export const options = {
  stages: [
    { duration: "10s", target: 10 },  // rampa de subida: 0 -> 10 usuarios virtuales
    { duration: "20s", target: 25 },  // estres: sube a 25 VUs sostenidos
    { duration: "10s", target: 0 },   // rampa de bajada
  ],
  thresholds: {
    http_req_duration: ["p(95)<800"],   // 95% de las respuestas en menos de 800ms
    http_req_failed: ["rate<0.01"],     // menos de 1% de errores
  },
};

const AUTH_URL = "http://host.docker.internal:8081";
const PRODUCT_URL = "http://host.docker.internal:8083";
const REPORT_URL = "http://host.docker.internal:8085";

export function setup() {
  const res = http.post(
    `${AUTH_URL}/api/auth/login`,
    JSON.stringify({ username: "operador", password: "operador123" }),
    { headers: { "Content-Type": "application/json" } }
  );
  const token = res.json("token");
  if (!token) {
    throw new Error("No se pudo obtener token de operador para la prueba de carga");
  }
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const resProductos = http.get(`${PRODUCT_URL}/api/productos`, { headers });
  check(resProductos, {
    "productos: status 200": (r) => r.status === 200,
    "productos: respuesta < 800ms": (r) => r.timings.duration < 800,
  });

  const resReporte = http.get(`${REPORT_URL}/api/reportes/stock-actual`, { headers });
  check(resReporte, {
    "reporte stock: status 200": (r) => r.status === 200,
    "reporte stock: respuesta < 800ms": (r) => r.timings.duration < 800,
  });

  sleep(1);
}
