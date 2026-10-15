require('dotenv').config();

const FALLBACK_PG_URL = 'postgresql://project_ubvw_user:mJVmvnu6HhybpOP5qKuWhEAoVW9N3k0i@dpg-db85c4gu01pc73ejgr30-a/project_ubvw';

const activeDbUrl =
  process.env.DATABASE_URL ||
  process.env.INTERNAL_DATABASE_URL ||
  process.env.POSTGRES_URL ||
  FALLBACK_PG_URL;

const {
  DB_HOST = 'localhost',
  DB_USER = 'root',
  DB_PASSWORD = '',
  DB_NAME = 'canteen_db'
} = process.env;

const isExplicitMysql = process.env.DB_CLIENT === 'mysql' || process.env.USE_MYSQL === 'true';
const isPostgres = !isExplicitMysql && (!!activeDbUrl || process.env.DB_CLIENT === 'postgres');

let pool;
let dbInterface;

function translateQueryForPg(sql, params = []) {
  let convertedSql = sql;
  convertedSql = convertedSql.replace(/\bAS\s+UNSIGNED\b/gi, 'AS BIGINT');
  convertedSql = convertedSql.replace(/\bCURDATE\(\)/gi, 'CURRENT_DATE');

  let convertedParams = params;

  // Handle MySQL bulk insert syntax: VALUES ? where params is [[[col1, col2], [col1, col2]]]
  if (convertedSql.includes('VALUES ?') && Array.isArray(params[0]) && Array.isArray(params[0][0])) {
    const rows = params[0];
    const flat = [];
    let pIdx = 1;
    const rowPlaceholders = rows.map(r => '(' + r.map(v => { flat.push(v); return '$' + (pIdx++); }).join(',') + ')');
    convertedSql = convertedSql.replace('VALUES ?', 'VALUES ' + rowPlaceholders.join(', '));
    convertedParams = flat;
  } else {
    let pIdx = 1;
    convertedSql = convertedSql.replace(/\?/g, () => '$' + (pIdx++));
  }

  // If INSERT statement doesn't have RETURNING, add RETURNING * to capture insertId
  const isInsert = /^\s*INSERT\s+INTO\s+/i.test(convertedSql);
  if (isInsert && !/\bRETURNING\b/i.test(convertedSql)) {
    convertedSql += ' RETURNING *';
  }

  return { sql: convertedSql, params: convertedParams };
}

async function executePgQuery(executor, sql, params = []) {
  const { sql: translatedSql, params: translatedParams } = translateQueryForPg(sql, params);
  try {
    const res = await executor.query(translatedSql, translatedParams);
    const rows = res.rows || [];
    const firstRow = rows[0];
    const insertId = firstRow ? (firstRow.token ?? firstRow.id ?? null) : null;
    rows.insertId = insertId;
    rows.affectedRows = res.rowCount || 0;
    rows.rowCount = res.rowCount || 0;
    return [rows, res.fields || []];
  } catch (err) {
    if (err.code === '23505') {
      err.code = 'ER_DUP_ENTRY';
    }
    throw err;
  }
}

async function initPostgres() {
  const { Pool } = require('pg');

  const connectionString = activeDbUrl;
  let ssl = false;
  if (connectionString && !connectionString.includes('localhost') && !connectionString.includes('127.0.0.1')) {
    ssl = { rejectUnauthorized: false };
  }

  pool = new Pool({
    connectionString,
    ssl
  });

  const client = await pool.connect();
  try {
    // 1. Create tables
    await client.query(`
      CREATE TABLE IF NOT EXISTS menu_items (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        price INT NOT NULL,
        stock INT NOT NULL DEFAULT 0,
        available BOOLEAN NOT NULL DEFAULT TRUE
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS orders (
        token SERIAL PRIMARY KEY,
        student_name VARCHAR(100) NOT NULL,
        pickup_slot VARCHAR(20) NOT NULL,
        total INT NOT NULL,
        pay_method VARCHAR(20) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'Placed',
        est_wait_min INT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        user_id INT NULL
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        email VARCHAR(150) NOT NULL UNIQUE,
        password_hash VARCHAR(300) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);

    await client.query(`ALTER TABLE orders ADD COLUMN IF NOT EXISTS user_id INT NULL`);

    await client.query(`
      CREATE TABLE IF NOT EXISTS order_items (
        id SERIAL PRIMARY KEY,
        order_token INT NOT NULL,
        item_id INT NOT NULL,
        name VARCHAR(100) NOT NULL,
        price INT NOT NULL,
        qty INT NOT NULL,
        FOREIGN KEY (order_token) REFERENCES orders(token) ON DELETE CASCADE
      )
    `);

    // Ensure order token sequence starts at 101 if empty
    try {
      await client.query(`
        SELECT setval(pg_get_serial_sequence('orders', 'token'), GREATEST(100, COALESCE((SELECT MAX(token) FROM orders), 100)), true)
      `);
    } catch (_) {
      try {
        await client.query(`ALTER SEQUENCE orders_token_seq RESTART WITH 101`);
      } catch (_) {}
    }

    // 2. Seed menu if empty
    const menuCountRes = await client.query('SELECT COUNT(*) AS c FROM menu_items');
    if (parseInt(menuCountRes.rows[0].c, 10) === 0) {
      await client.query(`
        INSERT INTO menu_items (name, price, stock) VALUES
          ('Masala Dosa', 50, 40),
          ('Veg Biryani', 80, 30),
          ('Chicken Biryani', 120, 25),
          ('Idli (3 pcs)', 30, 50),
          ('Samosa', 15, 60),
          ('Veg Sandwich', 40, 35),
          ('Masala Chai', 12, 100),
          ('Cold Coffee', 45, 40)
      `);
    }

    console.log('PostgreSQL ready (connected to ' + (pool.options.connectionString ? 'Render Database' : 'PostgreSQL') + ')');
  } finally {
    client.release();
  }

  dbInterface = {
    query: (sql, params) => executePgQuery(pool, sql, params),
    getConnection: async () => {
      const conn = await pool.connect();
      return {
        query: (sql, params) => executePgQuery(conn, sql, params),
        beginTransaction: () => conn.query('BEGIN'),
        commit: () => conn.query('COMMIT'),
        rollback: () => conn.query('ROLLBACK'),
        release: () => conn.release()
      };
    }
  };
}

async function initMysql() {
  const mysql = require('mysql2/promise');

  const conn = await mysql.createConnection({ host: DB_HOST, user: DB_USER, password: DB_PASSWORD });
  await conn.query(`CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\``);
  await conn.end();

  pool = mysql.createPool({ host: DB_HOST, user: DB_USER, password: DB_PASSWORD, database: DB_NAME, connectionLimit: 10 });

  await pool.query(`CREATE TABLE IF NOT EXISTS menu_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price INT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    available TINYINT(1) NOT NULL DEFAULT 1
  )`);
  await pool.query(`CREATE TABLE IF NOT EXISTS orders (
    token INT AUTO_INCREMENT PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL,
    pickup_slot VARCHAR(20) NOT NULL,
    total INT NOT NULL,
    pay_method VARCHAR(20) NOT NULL,
    status ENUM('Placed','Preparing','Ready','Collected') NOT NULL DEFAULT 'Placed',
    est_wait_min INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  ) AUTO_INCREMENT = 101`);
  await pool.query(`CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(300) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  )`);
  const [col] = await pool.query(
    "SELECT COUNT(*) c FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'user_id'", [DB_NAME]);
  if (col[0].c === 0) await pool.query('ALTER TABLE orders ADD COLUMN user_id INT NULL');
  await pool.query(`CREATE TABLE IF NOT EXISTS order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_token INT NOT NULL,
    item_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price INT NOT NULL,
    qty INT NOT NULL,
    FOREIGN KEY (order_token) REFERENCES orders(token) ON DELETE CASCADE
  )`);

  const [[{ c }]] = await pool.query('SELECT COUNT(*) c FROM menu_items');
  if (c === 0) {
    await pool.query('INSERT INTO menu_items (name, price, stock) VALUES ?', [[
      ['Masala Dosa', 50, 40], ['Veg Biryani', 80, 30], ['Chicken Biryani', 120, 25], ['Idli (3 pcs)', 30, 50],
      ['Samosa', 15, 60], ['Veg Sandwich', 40, 35], ['Masala Chai', 12, 100], ['Cold Coffee', 45, 40]
    ]]);
  }
  console.log('MySQL ready: database "' + DB_NAME + '"');

  dbInterface = pool;
}

async function init() {
  if (isPostgres) {
    await initPostgres();
  } else {
    await initMysql();
  }
}

module.exports = {
  init,
  db: () => dbInterface
};
