const BASE = '/api';

async function request(url, options = {}) {
  const res = await fetch(`${BASE}${url}`, {
    headers: { ...options.headers },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text();
    const err = new Error(text || `Error ${res.status}`);
    err.status = res.status;
    throw err;
  }
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return res.json();
  }
  return res.text();
}

function formEncode(data) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(data)) {
    params.append(key, String(value));
  }
  return params;
}

export const donors = {
  list: () => request('/donantes'),
  get: (id) => request(`/donantes/${id}`),
  create: (data) =>
    request('/donantes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: formEncode(data),
    }),
  available: () => request('/donantes/disponibles'),
  reserved: () => request('/donantes/reservados'),
  byType: (tipo) => request(`/donantes/tipo/${tipo}`),
  reserve: (id) => request(`/donantes/${id}/reservar`, { method: 'PUT' }),
  release: (id) => request(`/donantes/${id}/liberar`, { method: 'PUT' }),
  compatible: (organo, sangre) =>
    request(`/donantes/compatibles?organo=${encodeURIComponent(organo)}&sangre=${encodeURIComponent(sangre)}`),
  stats: () => request('/donantes/estadisticas'),
};

export const reservations = {
  list: () => request('/reservas'),
  byDonor: (idDonante) => request(`/reservas/donante/${idDonante}`),
  countByDonor: (idDonante) => request(`/reservas/donante/${idDonante}/count`),
  create: (data) =>
    request('/reservas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: formEncode(data),
    }),
};

export const logs = {
  list: () => request('/logs'),
};

export const health = {
  check: () => fetch('/actuator/health').then((r) => r.json()),
};
