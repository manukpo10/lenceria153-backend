import express from "express";
import cors from "cors";
import authRouter from "./routes/auth";
import productosRouter from "./routes/productos";
import ventasRouter from "./routes/ventas";
import cajaRouter from "./routes/caja";

const app = express();
const PORT = process.env.PORT ?? 3001;

app.use(cors({ origin: "http://localhost:3000", credentials: true }));
app.use(express.json());

app.get("/health", (_req, res) => res.json({ status: "ok", timestamp: new Date().toISOString() }));

app.use("/api/auth", authRouter);
app.use("/api/productos", productosRouter);
app.use("/api/ventas", ventasRouter);
app.use("/api/caja", cajaRouter);

app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error("[Error]", err);
  res.status(500).json({ error: "Error interno del servidor" });
});

app.listen(PORT, () => {
  console.log(`Backend corriendo en http://localhost:${PORT}`);
  console.log(`Health: http://localhost:${PORT}/health`);
});

export default app;