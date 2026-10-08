require('dotenv').config();
const mysql = require('mysql2/promise');

const { DB_HOST = 'localhost', DB_USER = 'root', DB_PASSWORD = '', DB_NAME = 'canteen_db' } = process.env;
let pool;

async function init() {
  // 1. create the database if it does not exist
  const conn = await mysql.createConnection({ host: DB_HOST, user: DB_USER, password: DB_PASSWORD });
  await conn.query(`CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\``);
  await conn.end();

  // 2. connect to it
  pool = mysql.createPool({ host: DB_HOST, user: DB_USER, password: DB_PASSWORD, database: DB_NAME, connectionLimit: 10 });

  // 3. create tables
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
  // add orders.user_id if this is an older database
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

  // 4. seed menu on first run
  const [[{ c }]] = await pool.query('SELECT COUNT(*) c FROM menu_items');
  if (c === 0) {
    await pool.query('INSERT INTO menu_items (name, price, stock) VALUES ?', [[
      ['Masala Dosa', 50, 40], ['Veg Biryani', 80, 30], ['Chicken Biryani', 120, 25], ['Idli (3 pcs)', 30, 50],
      ['Samosa', 15, 60], ['Veg Sandwich', 40, 35], ['Masala Chai', 12, 100], ['Cold Coffee', 45, 40]
    ]]);
  }
  console.log('MySQL ready: database "' + DB_NAME + '"');
}
module.exports = { init, db: () => pool };
