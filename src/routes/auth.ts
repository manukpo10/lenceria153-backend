import { Router, Request, Response } from "express";
import { readJson, insert, update } from "../lib/db";
import { generateToken } from "../middleware/auth";
import type { User } from "../lib/types";
import { v4 as uuid } from "uuid";
import crypto from "crypto";

const router = Router();

function hashPassword(password: string): string {
  return crypto.createHash("sha256").update(password).digest("hex");
}

function userToSafe(u: User) {
  const { passwordHash, ...rest } = u;
  return rest;
}

router.post("/login", (req: Request, res: Response) => {
  const { username, password } = req.body as { username: string; password: string };
  if (!username || !password) {
    res.status(400).json({ error: "Usuario y contraseña requeridos" });
    return;
  }
  const users = readJson<User>("users.json");
  const user = users.find((u) => u.username === username && u.passwordHash === hashPassword(password));
  if (!user) {
    res.status(401).json({ error: "Credenciales inválidas" });
    return;
  }
  const token = generateToken({ userId: user.id, username: user.username, role: user.role });
  res.json({ token, user: userToSafe(user) });
});

router.post("/register", (req: Request, res: Response) => {
  const { username, password, nombre, role = "vendedor" } = req.body as {
    username: string;
    password: string;
    nombre: string;
    role?: "admin" | "vendedor";
  };
  if (!username || !password || !nombre) {
    res.status(400).json({ error: "Todos los campos son requeridos" });
    return;
  }
  const users = readJson<User>("users.json");
  if (users.find((u) => u.username === username)) {
    res.status(409).json({ error: "El usuario ya existe" });
    return;
  }
  const user: User = {
    id: uuid(),
    username,
    passwordHash: hashPassword(password),
    role,
    nombre,
  };
  insert("users.json", user);
  const token = generateToken({ userId: user.id, username: user.username, role: user.role });
  res.status(201).json({ token, user: userToSafe(user) });
});

router.post("/seed", (_req: Request, res: Response) => {
  const users = readJson<User>("users.json");
  if (users.length > 0) {
    res.json({ message: "Ya hay usuarios" });
    return;
  }
  const defaultUsers: User[] = [
    { id: uuid(), username: "admin", passwordHash: hashPassword("admin123"), role: "admin", nombre: "Tío" },
    { id: uuid(), username: "vendedor", passwordHash: hashPassword("vendedor123"), role: "vendedor", nombre: "Vendedor" },
  ];
  users.push(...defaultUsers);
  const fs = require("fs");
  const path = require("path");
  const dataDir = path.join(__dirname, "../../data");
  if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });
  fs.writeFileSync(path.join(dataDir, "users.json"), JSON.stringify(defaultUsers, null, 2));
  res.json({ message: "Usuarios creados", users: defaultUsers.map(userToSafe) });
});

export default router;