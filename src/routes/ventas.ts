import { Router, Request, Response } from "express";
import { readJson, insert, update, writeJson } from "../lib/db";
import { authMiddleware } from "../middleware/auth";
import type { Venta, VentaItem, Producto, MovimientoCaja } from "../lib/types";
import { v4 as uuid } from "uuid";

const router = Router();

router.use(authMiddleware);

router.get("/", (req: Request, res: Response) => {
  const { desde, hasta, medio, userId } = req.query;
  let ventas = readJson<Venta>("ventas.json");
  if (desde) {
    const d = new Date(desde as string);
    ventas = ventas.filter((v) => new Date(v.fecha) >= d);
  }
  if (hasta) {
    const h = new Date(hasta as string);
    ventas = ventas.filter((v) => new Date(v.fecha) <= h);
  }
  if (medio) {
    ventas = ventas.filter((v) => v.medioPago === medio);
  }
  if (userId) {
    ventas = ventas.filter((v) => v.userId === userId);
  }
  res.json(ventas.sort((a, b) => new Date(b.fecha).getTime() - new Date(a.fecha).getTime()));
});

router.get("/:id", (req: Request, res: Response) => {
  const venta = readJson<Venta>("ventas.json").find((v) => v.id === req.params.id);
  if (!venta) {
    res.status(404).json({ error: "Venta no encontrada" });
    return;
  }
  res.json(venta);
});

router.post("/", async (req: Request, res: Response) => {
  const { items, medioPago, descuento = 0 } = req.body as {
    items: Array<{ productoId: string; cantidad: number }>;
    medioPago: Venta["medioPago"];
    descuento?: number;
  };
  if (!items?.length || !medioPago) {
    res.status(400).json({ error: "Items y medio de pago requeridos" });
    return;
  }
  if (!req.user) {
    res.status(401).json({ error: "No autenticado" });
    return;
  }

  // Cargar productos y verificar stock
  const productos = readJson<Producto>("productos.json");
  const ventaItems: VentaItem[] = [];
  let subtotal = 0;

  for (const item of items) {
    const producto = productos.find((p) => p.id === item.productoId);
    if (!producto) {
      res.status(400).json({ error: `Producto ${item.productoId} no encontrado` });
      return;
    }
    if (producto.stock < item.cantidad) {
      res.status(409).json({
        error: `Stock insuficiente para ${producto.descripcion}. Disponible: ${producto.stock}`,
      });
      return;
    }
    const precio = producto.precioUnidad ?? producto.precio;
    ventaItems.push({
      productoId: producto.id,
      codigo: producto.codigo,
      descripcion: producto.descripcion,
      cantidad: item.cantidad,
      precio,
      subtotal: precio * item.cantidad,
    });
    subtotal += precio * item.cantidad;
  }

  const total = subtotal - descuento;

  // Decrementar stock de cada producto
  for (const item of items) {
    update<Producto>(
      "productos.json",
      (p) => p.id === item.productoId,
      (p) => ({ ...p, stock: p.stock - item.cantidad })
    );
  }

  // Crear la venta
  const venta: Venta = {
    id: `V${Date.now().toString().slice(-8)}`,
    fecha: new Date().toISOString(),
    items: ventaItems,
    subtotal,
    descuento,
    total,
    medioPago,
    userId: req.user.userId,
    usuarioNombre: req.user.username,
    createdAt: new Date().toISOString(),
  };
  insert("ventas.json", venta);

  // Registrar movimiento en caja
  const movimiento: MovimientoCaja = {
    id: uuid(),
    cajaId: "caja-actual",
    tipo: "venta",
    monto: medioPago === "efectivo" ? total : 0,
    medioPago,
    descripcion: `Venta ${venta.id}`,
    userId: req.user.userId,
    usuarioNombre: req.user.username,
    createdAt: new Date().toISOString(),
  };
  insert("movimientos.json", movimiento);

  res.status(201).json(venta);
});

router.get("/stats/resumen", (req: Request, res: Response) => {
  const ventas = readJson<Venta>("ventas.json");
  const productos = readJson<Producto>("productos.json");
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);

  const ventasHoy = ventas.filter((v) => new Date(v.fecha) >= hoy);
  const totalHoy = ventasHoy.reduce((s, v) => s + v.total, 0);
  const itemsHoy = ventasHoy.reduce(
    (s, v) => s + v.items.reduce((x, i) => x + i.cantidad, 0),
    0
  );

  const stockBajo = productos.filter((p) => p.stock > 0 && p.stock <= 3).length;
  const sinStock = productos.filter((p) => p.stock === 0).length;

  // Totales últimos 30 días
  const hace30 = new Date();
  hace30.setDate(hace30.getDate() - 30);
  const ultimos30 = ventas.filter((v) => new Date(v.fecha) >= hace30);
  const total30d = ultimos30.reduce((s, v) => s + v.total, 0);

  // medios de pago hoy
  const medios = Object.fromEntries(
    ["efectivo", "debito", "credito", "transferencia", "qr"].map((m) => [
      m,
      ventasHoy.filter((v) => v.medioPago === m).reduce((s, v) => s + v.total, 0),
    ])
  );

  res.json({
    ventasHoy: ventasHoy.length,
    totalHoy,
    itemsHoy,
    stockBajo,
    sinStock,
    total30dias: total30d,
    medios,
  });
});

export default router;