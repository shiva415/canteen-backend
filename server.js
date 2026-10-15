require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { init, db } = require('./db');

const app = express();
const PORT = process.env.PORT || 5000;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';
app.use(cors());
app.use(express.json());

app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));
app.get('/', (req, res) => res.json({ message: 'Smart Campus Canteen API', status: 'running' }));

const wrap = fn => (req, res) => fn(req, res).catch(e => { console.error(e); res.status(500).json({ error: 'Server error' }); });
const admin = (req, res, next) => req.headers['x-admin'] === ADMIN_PASSWORD ? next() : res.status(401).json({ error: 'Unauthorized' });

async function getOrder(token) {
  const [[o]] = await db().query('SELECT * FROM orders WHERE token = ?', [token]);
  if (!o) return null;
  const [items] = await db().query('SELECT name, price, qty FROM order_items WHERE order_token = ?', [token]);
  return { token: o.token, name: o.student_name, slot: o.pickup_slot, total: o.total, payMethod: o.pay_method,
           status: o.status, estWaitMin: o.est_wait_min, createdAt: o.created_at, userId: o.user_id, items };
}


// ---------- Auth helpers (built-in crypto, no extra packages) ----------
const crypto = require('crypto');
const SECRET = process.env.AUTH_SECRET || 'change-this-secret';
const hashPw = (pw) => { const salt = crypto.randomBytes(16).toString('hex'); return salt + ':' + crypto.scryptSync(pw, salt, 64).toString('hex'); };
const checkPw = (pw, stored) => { const [salt, h] = stored.split(':'); return crypto.timingSafeEqual(crypto.scryptSync(pw, salt, 64), Buffer.from(h, 'hex')); };
const sign = (p) => { const b = Buffer.from(JSON.stringify(p)).toString('base64url'); return b + '.' + crypto.createHmac('sha256', SECRET).update(b).digest('base64url'); };
const verify = (t) => {
  try {
    const [b, s] = t.split('.'); const e = crypto.createHmac('sha256', SECRET).update(b).digest('base64url');
    if (s.length !== e.length || !crypto.timingSafeEqual(Buffer.from(s), Buffer.from(e))) return null;
    const p = JSON.parse(Buffer.from(b, 'base64url').toString()); return p.exp > Date.now() ? p : null;
  } catch { return null; }
};
const needUser = async (req, res, next) => {
  try {
    const p = verify((req.headers.authorization || '').replace('Bearer ', ''));
    if (!p) return res.status(401).json({ error: 'Please log in' });
    const [[u]] = await db().query('SELECT id, name, email FROM users WHERE id = ?', [p.id]);
    if (!u) return res.status(401).json({ error: 'Please log in' });
    req.user = u; next();
  } catch (e) { res.status(500).json({ error: 'Server error' }); }
};
const session = (u) => ({ token: sign({ id: u.id, exp: Date.now() + 7 * 24 * 3600 * 1000 }), user: { id: u.id, name: u.name, email: u.email } });

app.post('/api/auth/register', wrap(async (req, res) => {
  const name = (req.body.name || '').trim(), email = (req.body.email || '').trim().toLowerCase(), password = req.body.password || '';
  if (!name || !/^\S+@\S+\.\S+$/.test(email) || password.length < 6)
    return res.status(400).json({ error: 'Enter name, a valid email and a password of at least 6 characters' });
  try {
    const [r] = await db().query('INSERT INTO users (name, email, password_hash) VALUES (?,?,?)', [name, email, hashPw(password)]);
    res.json(session({ id: r.insertId, name, email }));
  } catch (e) {
    if (e.code === 'ER_DUP_ENTRY' || e.code === '23505') return res.status(400).json({ error: 'This email is already registered' });
    throw e;
  }
}));

app.post('/api/auth/login', wrap(async (req, res) => {
  const email = (req.body.email || '').trim().toLowerCase();
  const [[u]] = await db().query('SELECT * FROM users WHERE email = ?', [email]);
  if (!u || !checkPw(req.body.password || '', u.password_hash)) return res.status(401).json({ error: 'Wrong email or password' });
  res.json(session(u));
}));

app.get('/api/auth/me', needUser, (req, res) => res.json(req.user));

// ---------- Student ----------
app.get('/api/menu', wrap(async (req, res) => {
  const [rows] = await db().query('SELECT id, name, price, stock, available FROM menu_items ORDER BY id');
  res.json(rows.map(r => ({ ...r, available: !!r.available })));
}));

app.post('/api/orders', needUser, wrap(async (req, res) => {
  const { items, slot, payMethod } = req.body;
  const name = req.user.name;
  if (!name || !Array.isArray(items) || !items.length || !slot)
    return res.status(400).json({ error: 'Name, items and pickup slot are required' });

  const conn = await db().getConnection();
  try {
    await conn.beginTransaction();
    let total = 0; const lines = [];
    for (const it of items) {
      const qty = parseInt(it.qty);
      const [[m]] = await conn.query('SELECT * FROM menu_items WHERE id = ? FOR UPDATE', [it.id]);
      if (!m || !m.available || !(qty > 0)) throw new Error('Invalid item');
      if (m.stock < qty) throw new Error(`Only ${m.stock} left of ${m.name}`);
      await conn.query('UPDATE menu_items SET stock = stock - ? WHERE id = ?', [qty, m.id]);
      lines.push([m.id, m.name, m.price, qty]);
      total += m.price * qty;
    }
    const [[{ pending }]] = await conn.query("SELECT COUNT(*) pending FROM orders WHERE status IN ('Placed','Preparing')");
    const est = Math.max(5, Math.min(30, 5 + (parseInt(pending, 10) || 0) * 2));
    const [r] = await conn.query(
      'INSERT INTO orders (student_name, pickup_slot, total, pay_method, est_wait_min, user_id) VALUES (?,?,?,?,?,?)',
      [name, slot, total, payMethod || 'UPI', est, req.user.id]);
    await conn.query('INSERT INTO order_items (order_token, item_id, name, price, qty) VALUES ?',
      [lines.map(l => [r.insertId, ...l])]);
    await conn.commit();
    res.json(await getOrder(r.insertId));
  } catch (e) {
    await conn.rollback();
    res.status(400).json({ error: e.message });
  } finally { conn.release(); }
}));

app.get('/api/my-orders', needUser, wrap(async (req, res) => {
  const [rows] = await db().query('SELECT token FROM orders WHERE user_id = ? ORDER BY token DESC LIMIT 20', [req.user.id]);
  res.json(await Promise.all(rows.map(r => getOrder(r.token))));
}));

app.get('/api/orders/:token', needUser, wrap(async (req, res) => {
  const o = await getOrder(req.params.token);
  if (!o || o.userId !== req.user.id) return res.status(404).json({ error: 'Token not found' });
  res.json(o);
}));

// ---------- Admin ----------
app.post('/api/admin/login', (req, res) =>
  req.body.password === ADMIN_PASSWORD ? res.json({ ok: true }) : res.status(401).json({ error: 'Wrong password' }));

app.get('/api/admin/orders', admin, wrap(async (req, res) => {
  const [rows] = await db().query('SELECT token FROM orders ORDER BY token DESC LIMIT 100');
  res.json(await Promise.all(rows.map(r => getOrder(r.token))));
}));

app.patch('/api/admin/orders/:token', admin, wrap(async (req, res) => {
  await db().query('UPDATE orders SET status = ? WHERE token = ?', [req.body.status, req.params.token]);
  res.json(await getOrder(req.params.token));
}));

app.patch('/api/admin/menu/:id', admin, wrap(async (req, res) => {
  const { price, stock, available } = req.body;
  if (price !== undefined) await db().query('UPDATE menu_items SET price = ? WHERE id = ?', [Number(price), req.params.id]);
  if (stock !== undefined) await db().query('UPDATE menu_items SET stock = ? WHERE id = ?', [Number(stock), req.params.id]);
  if (available !== undefined) await db().query('UPDATE menu_items SET available = ? WHERE id = ?', [available ? 1 : 0, req.params.id]);
  res.json({ ok: true });
}));

app.post('/api/admin/menu', admin, wrap(async (req, res) => {
  const { name, price, stock } = req.body;
  if (!name || !(price > 0)) return res.status(400).json({ error: 'Name and price required' });
  await db().query('INSERT INTO menu_items (name, price, stock) VALUES (?,?,?)', [name, Number(price), Number(stock) || 0]);
  res.json({ ok: true });
}));

app.get('/api/admin/analytics', admin, wrap(async (req, res) => {
  const [[t]] = await db().query('SELECT COUNT(*) n, COALESCE(SUM(total),0) r FROM orders WHERE DATE(created_at) = CURDATE()');
  const [[a]] = await db().query('SELECT COUNT(*) n, COALESCE(SUM(total),0) r FROM orders');
  const [top] = await db().query('SELECT name, CAST(SUM(qty) AS UNSIGNED) c FROM order_items GROUP BY name ORDER BY c DESC LIMIT 5');
  res.json({ todayOrders: t.n, todayRevenue: Number(t.r), totalOrders: a.n, totalRevenue: Number(a.r),
             topItems: top.map(x => [x.name, Number(x.c)]) });
}));

init().then(() => app.listen(PORT, '0.0.0.0', () => console.log(`API running on port ${PORT}`)))
  .catch(e => { console.error('\nCould not connect to database. Check DATABASE_URL or backend/.env configuration.\n', e); process.exit(1); });
