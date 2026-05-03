import { Router, Request, Response } from "express";
import { readJson, insert, update, writeJson } from "../lib/db";
import { authMiddleware, adminOnly } from "../middleware/auth";
import type { Caja, MovimientoCaja, Venta } from "../lib/types";
import { v4 as uuid } from "uuid";

const router = Router();

router.use(authMiddleware);

router.get("/estado", (_req: Request, res: Response) => {
  const cajas = readJson<Caja>("cajas.json");
  const abierta = cajas.find((c) => c.estado === "abierta");
  if (!abierta) {
    res.json({ estado: "cerrada", caja: null });
    return;
  }
  // Calcular monto del sistema basado en movimientos
  const movs = readJson<MovimientoCaja>("movimientos.json").filter(
    (m) => m.cajaId === abierta.id
  );
  const montoSistema = movs.reduce((s, m) => {
    if (m.tipo === "apertura" || m.tipo === "ingreso" || m.tipo === "venta") return s + m.monto;
    if (m.tipo === "retiro" || m.tipo === "gasto" || m.tipo === "cierre") return s - Math.abs(m.monto);
    return s;
  }, abierta.montoApertura);

  res.json({
    estado: "abierta",
    caja: { ...abierta, montoSistema, diferencia: montoSistema - abierta.montoApertura },
  });
});

router.post("/abrir", (req: Request, res: Response) => {
  if (!req.user) { res.status(401).json({ error: "No autenticado" }); return; }
  const { montoApertura = 0 } = req.body as { montoApertura?: number };

  // Cerrar cualquier caja abierta previamente
  const cajas = readJson<Caja>("cajas.json");
  const abierta = cajas.find((c) => c.estado === "abierta");
  if (abierta) {
    res.status(409).json({ error: "Ya hay una caja abierta. Ciérrala primero.", cajaId: abierta.id });
    return;
  }

  const caja: Caja = {
    id: uuid(),
    nombre: "Caja Principal",
    estado: "abierta",
    aperturaId: null,
    cierreId: null,
    montoApertura,
    montoSistema: montoApertura,
    diferencia: 0,
    usuarioApertura: req.user.username,
    fechaApertura: new Date().toISOString(),
    usuarioCierre: null,
    fechaCierre: null,
    createdAt: new Date().toISOString(),
  };
  insert("cajas.json", caja);

  const mov: MovimientoCaja = {
    id: uuid(),
    cajaId: caja.id,
    tipo: "apertura",
    monto: montoApertura,
    descripcion: "Apertura de caja",
    userId: req.user.userId,
    usuarioNombre: req.user.username,
    createdAt: new Date().toISOString(),
  };
  insert("movimientos.json", mov);

  res.status(201).json({ message: "Caja abierta", caja });
});

router.post("/cerrar", (req: Request, res: Response) => {
  if (!req.user) { res.status(401).json({ error: "No autenticado" }); return; }
  const { montoReal } = req.body as { montoReal: number };
  if (montoReal === undefined) {
    res.status(400).json({ error: "montoReal es requerido para cerrar" });
    return;
  }

  const cajas = readJson<Caja>("cajas.json");
  const abierta = cajas.find((c) => c.estado === "abierta");
  if (!abierta) {
    res.status(409).json({ error: "No hay caja abierta" });
    return;
  }

  // Calcular monto sistema
  const movs = readJson<MovimientoCaja>("movimientos.json").filter((m) => m.cajaId === abierta.id);
  const montoSistema = movs.reduce((s, m) => {
    if (m.tipo === "apertura" || m.tipo === "ingreso" || m.tipo === "venta") return s + m.monto;
    if (m.tipo === "retiro" || m.tipo === "gasto" || m.tipo === "cierre") return s - Math.abs(m.monto);
    return s;
  }, abierta.montoApertura);

  const diferencia = montoReal - montoSistema;

  const cerrada = update<Caja>(
    "cajas.json",
    (c) => c.id === abierta.id,
    (c) => ({
      ...c,
      estado: "cerrada",
      montoSistema,
      diferencia,
      usuarioCierre: req.user!.username,
      fechaCierre: new Date().toISOString(),
    })
  )!;

  const mov: MovimientoCaja = {
    id: uuid(),
    cajaId: abierta.id,
    tipo: "cierre",
    monto: montoReal,
    descripcion: `Cierre de caja. Sistema: $${montoSistema.toFixed(2)} | Real: $${montoReal.toFixed(2)} | Diferencia: $${diferencia.toFixed(2)}`,
    userId: req.user.userId,
    usuarioNombre: req.user.username,
    createdAt: new Date().toISOString(),
  };
  insert("movimientos.json", mov);

  res.json({ message: "Caja cerrada", caja: { ...cerrada, montoReal, montoSistema, diferencia } });
});

router.post("/movimiento", (req: Request, res: Response) => {
  if (!req.user) { res.status(401).json({ error: "No autenticado" }); return; }
  const { tipo, monto, descripcion, medioPago } = req.body as {
    tipo: "ingreso" | "retiro" | "gasto";
    monto: number;
    descripcion: string;
    medioPago?: "efectivo" | "debito" | "credito" | "transferencia" | "qr";
  };
  if (!tipo || !monto || !descripcion) {
    res.status(400).json({ error: "tipo, monto y descripcion son requeridos" });
    return;
  }

  const cajas = readJson<Caja>("cajas.json");
  const abierta = cajas.find((c) => c.estado === "abierta");
  if (!abierta) {
    res.status(409).json({ error: "No hay caja abierta" });
    return;
  }

  const mov: MovimientoCaja = {
    id: uuid(),
    cajaId: abierta.id,
    tipo,
    monto,
    medioPago,
    descripcion,
    userId: req.user.userId,
    usuarioNombre: req.user.username,
    createdAt: new Date().toISOString(),
  };
  insert("movimientos.json", mov);
  res.status(201).json(mov);
});

router.get("/movimientos", (req: Request, res: Response) => {
  const { cajaId } = req.query;
  let movs = readJson<MovimientoCaja>("movimientos.json");
  if (cajaId) {
    movs = movs.filter((m) => m.cajaId === cajaId);
  }
  res.json(movs.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
});

router.get("/arqueo", (req: Request, res: Response) => {
  const cajas = readJson<Caja>("cajas.json");
  const abierta = cajas.find((c) => c.estado === "abierta");
  if (!abierta) {
    res.status(409).json({ error: "No hay caja abierta" });
    return;
  }

  const movs = readJson<MovimientoCaja>("movimientos.json")
    .filter((m) => m.cajaId === abierta.id)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());

  // Totales por tipo
  const totales = movs.reduce(
    (acc, m) => {
      if (m.tipo === "apertura") acc.apertura += m.monto;
      else if (m.tipo === "ingreso") acc.ingresos += m.monto;
      else if (m.tipo === "venta") acc.ventas += m.monto;
      else if (m.tipo === "retiro" || m.tipo === "gasto") acc.retiros += Math.abs(m.monto);
      else if (m.tipo === "cierre") acc.cierre += Math.abs(m.monto);
      return acc;
    },
    { apertura: 0, ingresos: 0, ventas: 0, retiros: 0, cierre: 0 }
  );

  // Totales por medio de pago
  const ventas = readJson<Venta>("ventas.json").filter(
    (v) => new Date(v.fecha) >= new Date(abierta.fechaApertura!)
  );
  const porMedio = Object.fromEntries(
    ["efectivo", "debito", "credito", "transferencia", "qr"].map((m) => [
      m,
      {
        count: ventas.filter((v) => v.medioPago === m).length,
        total: ventas.filter((v) => v.medioPago === m).reduce((s, v) => s + v.total, 0),
      },
    ])
  );

  const montoSistema = abierta.montoApertura + totales.ventas + totales.ingresos - totales.retiros;

  res.json({
    caja: { ...abierta, montoSistema, diferencia: 0 },
    movimientos: movs,
    resumen: { ...totales, montoSistema },
    ventasPorMedio: porMedio,
    cantidadVentas: ventas.length,
    itemsVendidos: ventas.reduce((s, v) => s + v.items.reduce((x, i) => x + i.cantidad, 0), 0),
  });
});

router.get("/historial", (req: Request, res: Response) => {
  const cajas = readJson<Caja>("cajas.json");
  res.json(cajas.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
});

export default router;