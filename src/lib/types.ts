export type User = {
  id: string;
  username: string;
  passwordHash: string;
  role: "admin" | "vendedor";
  nombre: string;
};

export type Session = {
  userId: string;
  username: string;
  role: "admin" | "vendedor";
  expiresAt: number;
};

export type Producto = {
  id: string;
  codigo: string;
  descripcion: string;
  rubro: string;
  costo: number | null;
  precio: number;
  precioUnidad: number | null;
  pack: number;
  stock: number;
  activo: boolean;
  createdAt: string;
  updatedAt: string;
};

export type VentaItem = {
  productoId: string;
  codigo: string;
  descripcion: string;
  cantidad: number;
  precio: number;
  subtotal: number;
};

export type Venta = {
  id: string;
  fecha: string;
  items: VentaItem[];
  subtotal: number;
  descuento: number;
  total: number;
  medioPago: "efectivo" | "debito" | "credito" | "transferencia" | "qr";
  userId: string;
  usuarioNombre: string;
  createdAt: string;
};

export type MovimientoCaja = {
  id: string;
  cajaId: string;
  tipo: "ingreso" | "retiro" | "apertura" | "cierre" | "venta" | "gasto";
  monto: number;
  medioPago?: "efectivo" | "debito" | "credito" | "transferencia" | "qr";
  descripcion: string;
  userId: string;
  usuarioNombre: string;
  createdAt: string;
};

export type Caja = {
  id: string;
  nombre: string;
  estado: "abierta" | "cerrada";
  aperturaId: string | null;
  cierreId: string | null;
  montoApertura: number;
  montoSistema: number;
  diferencia: number;
  usuarioApertura: string;
  fechaApertura: string | null;
  usuarioCierre: string | null;
  fechaCierre: string | null;
  createdAt: string;
};

export type JwtPayload = {
  userId: string;
  username: string;
  role: "admin" | "vendedor";
};

declare global {
  namespace Express {
    interface Request {
      user?: JwtPayload;
    }
  }
}