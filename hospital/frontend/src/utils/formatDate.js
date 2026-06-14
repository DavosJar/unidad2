export function formatTimestamp(ts) {
  if (!ts) return '';
  let date;
  if (Array.isArray(ts)) {
    const [y, m, d, h, min, s = 0] = ts;
    date = new Date(y, m - 1, d, h, min, s);
  } else if (typeof ts === 'string') {
    date = new Date(ts);
  } else {
    return '';
  }
  if (isNaN(date.getTime())) return '';
  return date.toLocaleString();
}
