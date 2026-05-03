package com.merceria153.controller;

import com.merceria153.dto.VentaRequest;
import com.merceria153.model.Venta;
import com.merceria153.service.VentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService svc;

    public VentaController(VentaService svc) {
        this.svc = svc;
    }

    @GetMapping
    public ResponseEntity<List<Venta>> listar(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) String medio) {
        return ResponseEntity.ok(svc.listar(desde, hasta, medio));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> obtener(@PathVariable String id) {
        return ResponseEntity.ok(svc.obtener(id));
    }

    @PostMapping
    public ResponseEntity<Venta> crear(@RequestBody VentaRequest req) {
        return ResponseEntity.status(201).body(svc.crear(req));
    }

    @GetMapping("/stats/resumen")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(svc.stats());
    }
}