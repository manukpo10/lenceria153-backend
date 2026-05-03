import { Router, Request, Response } from "express";
import { readJson, insert, update, remove } from "../lib/db";
import { authMiddleware, adminOnly } from "../middleware/auth";
import type { Producto } from "../lib/types";
import { v4 as uuid } from "uuid";

const router = Router();

router.use(authMiddleware);

router.get("/", (req: Request, res: Response) => {
  const { q, rubro, activo } = req.query;
  let productos = readJson<Producto>("productos.json");
  if (rubro && rubro !== "TODOS") {
    productos = productos.filter((p) => p.rubro === rubro);
  }
  if (activo !== undefined) {
    productos = productos.filter((p) => p.activo === (activo === "true"));
  }
  if (q && typeof q === "string") {
    const term = q.toLowerCase();
    productos = productos.filter(
      (p) => p.codigo.includes(term) || p.descripcion.toLowerCase().includes(term)
    );
  }
  res.json(productos.slice(0, 100));
});

router.get("/rubros", (_req: Request, res: Response) => {
  const productos = readJson<Producto>("productos.json");
  const rubros = Array.from(new Set(productos.map((p) => p.rubro))).sort();
  res.json(rubros);
});

router.get("/:id", (req: Request, res: Response) => {
  const producto = readJson<Producto>("productos.json").find((p) => p.id === req.params.id);
  if (!producto) {
    res.status(404).json({ error: "Producto no encontrado" });
    return;
  }
  res.json(producto);
});

router.post("/", adminOnly, (req: Request, res: Response) => {
  const data = req.body as Partial<Producto>;
  if (!data.codigo || !data.descripcion || data.precio === undefined) {
    res.status(400).json({ error: "codigo, descripcion y precio son requeridos" });
    return;
  }
  const productos = readJson<Producto>("productos.json");
  if (productos.find((p) => p.codigo === data.codigo)) {
    res.status(409).json({ error: "Ya existe un producto con ese código" });
    return;
  }
  const producto: Producto = {
    id: uuid(),
    codigo: data.codigo,
    descripcion: data.descripcion,
    rubro: data.rubro ?? "OTROS",
    costo: data.costo ?? null,
    precio: data.precio,
    precioUnidad: data.precioUnidad ?? null,
    pack: data.pack ?? 1,
    stock: data.stock ?? 0,
    activo: data.activo ?? true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  insert("productos.json", producto);
  res.status(201).json(producto);
});

router.put("/:id", adminOnly, (req: Request, res: Response) => {
  const updated = update<Producto>(
    "productos.json",
    (p) => p.id === req.params.id,
    (p) => ({ ...p, ...req.body, id: p.id, updatedAt: new Date().toISOString() })
  );
  if (!updated) {
    res.status(404).json({ error: "Producto no encontrado" });
    return;
  }
  res.json(updated);
});

router.delete("/:id", adminOnly, (req: Request, res: Response) => {
  // Soft delete: marcar como inactivo en vez de borrar
  const updated = update<Producto>(
    "productos.json",
    (p) => p.id === req.params.id,
    (p) => ({ ...p, activo: false, updatedAt: new Date().toISOString() })
  );
  if (!updated) {
    res.status(404).json({ error: "Producto no encontrado" });
    return;
  }
  res.json({ message: "Producto desactivado" });
});

router.post("/seed", adminOnly, (req: Request, res: Response) => {
  const data = require("../../data/productos.json");
  const productos = readJson<Producto>("productos.json");
  if (productos.length > 0) {
    res.status(409).json({ error: "Ya hay productos cargados" });
    return;
  }
  const mapped: Producto[] = data.map((p: any) => ({
    id: uuid(),
    codigo: String(p.codigo),
    descripcion: p.descripcion,
    rubro: p.rubro,
    costo: p.costo ?? null,
    precio: p.precio,
    precioUnidad: p.precioUnidad ?? null,
    pack: p.pack ?? 1,
    stock: p.stock ?? 0,
    activo: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }));
  const fs = require("fs");
  const path = require("path");
  fs.writeFileSync(path.join(__dirname, "../../data/productos.json"), JSON.stringify(mapped, null, 2));
  res.json({ message: `${mapped.length} productos cargados` });
});

export default router;