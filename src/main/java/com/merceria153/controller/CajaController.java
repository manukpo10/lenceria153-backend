package com.merceria153.controller;

import com.merceria153.model.Caja;
import com.merceria153.model.MovimientoCaja;
import com.merceria153.service.CajaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService svc;

    public CajaController(CajaService svc) {
        this.svc = svc;
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok(svc.estado());
    }

    @PostMapping("/abrir")
    public ResponseEntity<Map<String, Object>> abrir(@RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(svc.abrir(body.get("montoApertura")));
    }

    @PostMapping("/cerrar")
    public ResponseEntity<Map<String, Object>> cerrar(@RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(svc.cerrar(body.get("montoReal")));
    }

    @PostMapping("/confirmar-cierre")
    public ResponseEntity<Map<String, Object>> confirmarCierre() {
        return ResponseEntity.ok(svc.confirmarCierre());
    }

    @PostMapping("/cancelar-cierre")
    public ResponseEntity<Map<String, Object>> cancelarCierre() {
        return ResponseEntity.ok(svc.cancelarCierre());
    }

    @PostMapping("/movimiento")
    public ResponseEntity<MovimientoCaja> movimiento(@RequestBody Map<String, String> body) {
        String tipo = body.get("tipo");
        BigDecimal monto = new BigDecimal(body.get("monto"));
        String desc = body.get("descripcion");
        String medio = body.get("medioPago");
        return ResponseEntity.status(201).body(svc.registrarMovimiento(tipo, monto, desc, medio));
    }

    @GetMapping("/movimientos")
    public ResponseEntity<List<MovimientoCaja>> movimientos(@RequestParam(required = false) String cajaId) {
        return ResponseEntity.ok(svc.movimientos(cajaId));
    }

    @GetMapping("/arqueo")
    public ResponseEntity<Map<String, Object>> arqueo() {
        return ResponseEntity.ok(svc.arqueo());
    }

    @GetMapping("/historial")
    public ResponseEntity<List<Caja>> historial() {
        return ResponseEntity.ok(svc.historial());
    }
}