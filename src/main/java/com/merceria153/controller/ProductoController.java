package com.merceria153.controller;

import com.merceria153.model.Producto;
import com.merceria153.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService svc;

    public ProductoController(ProductoService svc) {
        this.svc = svc;
    }

    @GetMapping
    public ResponseEntity<List<Producto>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rubro,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(svc.listar(q, rubro, activo));
    }

    @GetMapping("/rubros")
    public ResponseEntity<List<String>> rubros() {
        return ResponseEntity.ok(svc.listarRubros());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtener(@PathVariable String id) {
        return ResponseEntity.ok(svc.obtener(id));
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto data) {
        return ResponseEntity.status(201).body(svc.crear(data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable String id, @RequestBody Producto data) {
        return ResponseEntity.ok(svc.actualizar(id, data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        svc.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanup(@RequestParam(defaultValue = "30") int dias) {
        int deleted = svc.cleanupInactivos(dias);
        return ResponseEntity.ok(Map.of("message", deleted + " productos eliminados definitivamente"));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed(@RequestBody List<Map<String, Object>> productos) {
        int count = svc.seedDesdeJson(productos);
        return ResponseEntity.ok(Map.of("message", count + " productos cargados"));
    }

    @PostMapping("/import-pdf")
    public ResponseEntity<Map<String, Object>> importPdf(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        System.out.println(">>> [Controller] import-pdf llamado - file: " + file.getOriginalFilename());
        try {
            return ResponseEntity.ok(svc.importarDesdePdf(file));
        } catch (Exception e) {
            System.out.println(">>> [Controller] ERROR: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Producto> setStock(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object stockObj = body.get("stock");
        if (stockObj == null) return ResponseEntity.badRequest().build();
        int stock = ((Number) stockObj).intValue();
        return ResponseEntity.ok(svc.setStock(id, stock));
    }

    @GetMapping("/export-stock-csv")
    public ResponseEntity<String> exportStockCsv() {
        String csv = svc.exportarStockCsv();
        return ResponseEntity.ok()
            .header("Content-Type", "text/csv; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=stock.csv")
            .body(csv);
    }

    @PostMapping("/import-stock-csv")
    public ResponseEntity<Map<String, Object>> importStockCsv(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return ResponseEntity.ok(svc.importarStockCsv(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/import-confirm")
    public ResponseEntity<Map<String, Object>> importConfirm(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productos = (List<Map<String, Object>>) body.get("productos");
        String modo = body.get("modo") != null ? body.get("modo").toString() : "all";
        return ResponseEntity.ok(svc.confirmarImportacion(productos, modo));
    }

    @PostMapping("/calcular-precios-venta")
    public ResponseEntity<Map<String, Object>> calcularPreciosVenta(@RequestBody Map<String, Object> body) {
        Object pctObj = body.get("porcentaje");
        if (pctObj == null) return ResponseEntity.badRequest().body(Map.of("error", "porcentaje requerido"));
        BigDecimal porcentaje = new BigDecimal(pctObj.toString());
        return ResponseEntity.ok(svc.calcularPreciosVenta(porcentaje));
    }

    @PostMapping("/calcular-precio-venta/{id}")
    public ResponseEntity<Map<String, Object>> calcularPrecioVenta(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object pctObj = body.get("porcentaje");
        if (pctObj == null) return ResponseEntity.badRequest().body(Map.of("error", "porcentaje requerido"));
        BigDecimal porcentaje = new BigDecimal(pctObj.toString());
        return ResponseEntity.ok(svc.calcularPrecioVenta(id, porcentaje));
    }

    @PostMapping("/reset-stock")
    public ResponseEntity<Map<String, Object>> resetStock(@RequestBody Map<String, Object> body) {
        Object stockObj = body.get("stock");
        int stock = stockObj != null ? ((Number) stockObj).intValue() : 999;
        int count = svc.resetearStock(stock);
        return ResponseEntity.ok(Map.of("actualizados", count, "stock", stock));
    }
}