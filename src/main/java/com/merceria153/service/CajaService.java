package com.merceria153.service;

import com.merceria153.model.*;
import com.merceria153.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CajaService {

    private final CajaRepository cajaRepo;
    private final MovimientoCajaRepository movRepo;
    private final VentaRepository ventaRepo;

    public CajaService(CajaRepository cajaRepo, MovimientoCajaRepository movRepo, VentaRepository ventaRepo) {
        this.cajaRepo = cajaRepo;
        this.movRepo = movRepo;
        this.ventaRepo = ventaRepo;
    }

    public Map<String, Object> estado() {
        Optional<Caja> abierta = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (abierta.isEmpty()) {
            Optional<Caja> pendiente = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
            if (pendiente.isPresent()) {
                Caja c = pendiente.get();
                BigDecimal montoSistema = calcularMontoSistema(c);
                Map<String, Object> cajaMap = new HashMap<>();
                cajaMap.put("id", c.getId());
                cajaMap.put("nombre", c.getNombre());
                cajaMap.put("estado", c.getEstado());
                cajaMap.put("montoApertura", c.getMontoApertura());
                cajaMap.put("montoSistema", montoSistema);
                cajaMap.put("montoRealTemporal", c.getMontoRealTemporal());
                cajaMap.put("diferencia", montoSistema.subtract(c.getMontoRealTemporal()));
                cajaMap.put("usuarioApertura", c.getUsuarioApertura());
                cajaMap.put("fechaApertura", c.getFechaApertura());
                return Map.of("estado", "pendiente_cierre", "caja", cajaMap);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("estado", "cerrada");
            result.put("caja", null);
            return result;
        }
        Caja c = abierta.get();
        BigDecimal montoSistema = calcularMontoSistema(c);
        return Map.of(
                "estado", "abierta",
                "caja", Map.of(
                        "id", c.getId(),
                        "nombre", c.getNombre(),
                        "estado", c.getEstado(),
                        "montoApertura", c.getMontoApertura(),
                        "montoSistema", montoSistema,
                        "diferencia", montoSistema.subtract(c.getMontoApertura()),
                        "usuarioApertura", c.getUsuarioApertura(),
                        "fechaApertura", c.getFechaApertura()
                )
        );
    }

    @Transactional
    public Map<String, Object> abrir(BigDecimal montoApertura) {
        Optional<Caja> yaAbierta = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (yaAbierta.isPresent()) {
            throw new RuntimeException("Ya hay una caja abierta. Ciérrala primero.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getDetails().toString();
        String userId = auth.getPrincipal().toString();

        Caja caja = new Caja();
        caja.setNombre("Caja Principal");
        caja.setEstado("abierta");
        caja.setMontoApertura(montoApertura != null ? montoApertura : BigDecimal.ZERO);
        caja.setMontoSistema(montoApertura != null ? montoApertura : BigDecimal.ZERO);
        caja.setUsuarioApertura(username);
        caja.setFechaApertura(LocalDateTime.now());
        caja = cajaRepo.save(caja);

        MovimientoCaja mov = new MovimientoCaja();
        mov.setCajaId(caja.getId());
        mov.setTipo("apertura");
        mov.setMonto(montoApertura != null ? montoApertura : BigDecimal.ZERO);
        mov.setDescripcion("Apertura de caja");
        mov.setUserId(userId);
        mov.setUsuarioNombre(username);
        movRepo.save(mov);

        return Map.of("message", "Caja abierta", "caja", caja);
    }

    @Transactional
    public Map<String, Object> cerrar(BigDecimal montoReal) {
        Optional<Caja> optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (optCaja.isEmpty()) {
            throw new RuntimeException("No hay caja abierta");
        }
        Caja caja = optCaja.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getDetails().toString();
        String userId = auth.getPrincipal().toString();

        BigDecimal montoSistema = calcularMontoSistema(caja);
        BigDecimal diferencia = montoReal.subtract(montoSistema);

        caja.setEstado("pendiente_cierre");
        caja.setMontoSistema(montoSistema);
        caja.setMontoRealTemporal(montoReal);
        caja.setDiferencia(diferencia);
        cajaRepo.save(caja);

        List<MovimientoCaja> movs = movRepo.findByCajaIdOrderByCreatedAtDesc(caja.getId());
        List<Venta> ventas = ventaRepo.findByFechaGreaterThanEqualOrderByFechaDesc(caja.getFechaApertura());

        BigDecimal apertura = BigDecimal.ZERO, ingresos = BigDecimal.ZERO;
        BigDecimal ventasTotal = BigDecimal.ZERO, retiros = BigDecimal.ZERO;
        for (MovimientoCaja m : movs) {
            switch (m.getTipo()) {
                case "apertura" -> apertura = apertura.add(m.getMonto());
                case "ingreso" -> ingresos = ingresos.add(m.getMonto());
                case "venta" -> ventasTotal = ventasTotal.add(m.getMonto());
                case "retiro", "gasto" -> retiros = retiros.add(m.getMonto());
            }
        }

        Map<String, Map<String, Object>> porMedio = new LinkedHashMap<>();
        for (String medio : List.of("efectivo", "debito", "credito", "transferencia", "qr")) {
            List<Venta> filtro = ventas.stream().filter(v -> v.getMedioPago().equals(medio)).toList();
            BigDecimal total = filtro.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            porMedio.put(medio, Map.of("count", filtro.size(), "total", total));
        }

        Map<String, Object> cajaData = new LinkedHashMap<>();
        cajaData.put("id", caja.getId());
        cajaData.put("montoApertura", apertura);
        cajaData.put("montoSistema", montoSistema);
        cajaData.put("montoReal", montoReal);
        cajaData.put("diferencia", diferencia);
        cajaData.put("usuarioApertura", caja.getUsuarioApertura());
        cajaData.put("fechaApertura", caja.getFechaApertura());

        Map<String, Object> resumenData = new LinkedHashMap<>();
        resumenData.put("apertura", apertura);
        resumenData.put("ventas", ventasTotal);
        resumenData.put("ingresos", ingresos);
        resumenData.put("retiros", retiros);
        resumenData.put("totalSistema", montoSistema);
        resumenData.put("totalReal", montoReal);
        resumenData.put("diferencia", diferencia);

        return Map.of(
                "message", "Cierre en proceso",
                "caja", cajaData,
                "resumen", resumenData,
                "ventasPorMedio", porMedio,
                "cantidadVentas", ventas.size(),
                "itemsVendidos", ventas.stream().mapToInt(v ->
                        v.getItems().stream().mapToInt(Venta.VentaItem::getCantidad).sum()).sum()
        );
    }

    @Transactional
    public Map<String, Object> confirmarCierre() {
        Optional<Caja> optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
        if (optCaja.isEmpty()) {
            throw new RuntimeException("No hay cierre pendiente");
        }
        Caja caja = optCaja.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getDetails().toString();
        String userId = auth.getPrincipal().toString();

        caja.setEstado("cerrada");
        caja.setUsuarioCierre(username);
        caja.setFechaCierre(LocalDateTime.now());
        cajaRepo.save(caja);

        MovimientoCaja mov = new MovimientoCaja();
        mov.setCajaId(caja.getId());
        mov.setTipo("cierre");
        mov.setMonto(caja.getMontoRealTemporal());
        mov.setDescripcion(String.format("Cierre — Sistema: %s | Real: %s | Diferencia: %s",
                caja.getMontoSistema(), caja.getMontoRealTemporal(), caja.getDiferencia()));
        mov.setUserId(userId);
        mov.setUsuarioNombre(username);
        movRepo.save(mov);

        return Map.of("message", "Caja cerrada", "caja", caja);
    }

    @Transactional
    public Map<String, Object> cancelarCierre() {
        Optional<Caja> optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
        if (optCaja.isEmpty()) {
            throw new RuntimeException("No hay cierre pendiente");
        }
        Caja caja = optCaja.get();

        caja.setEstado("abierta");
        caja.setMontoSistema(caja.getMontoApertura());
        caja.setMontoRealTemporal(BigDecimal.ZERO);
        caja.setDiferencia(BigDecimal.ZERO);
        cajaRepo.save(caja);

        return Map.of("message", "Cierre cancelado", "caja", caja);
    }

    @Transactional
    public MovimientoCaja registrarMovimiento(String tipo, BigDecimal monto, String descripcion, String medioPago) {
        Optional<Caja> optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (optCaja.isEmpty()) {
            optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
        }
        if (optCaja.isEmpty()) {
            throw new RuntimeException("No hay caja abierta");
        }
        Caja caja = optCaja.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getDetails().toString();
        String userId = auth.getPrincipal().toString();

        MovimientoCaja mov = new MovimientoCaja();
        mov.setCajaId(caja.getId());
        mov.setTipo(tipo);
        mov.setMonto(monto);
        mov.setDescripcion(descripcion);
        mov.setMedioPago(medioPago);
        mov.setUserId(userId);
        mov.setUsuarioNombre(username);
        return movRepo.save(mov);
    }

    public List<MovimientoCaja> movimientos(String cajaId) {
        if (cajaId != null) {
            return movRepo.findByCajaIdOrderByCreatedAtDesc(cajaId);
        }
        Optional<Caja> abierta = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (abierta.isEmpty()) {
            abierta = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
        }
        if (abierta.isPresent()) {
            return movRepo.findByCajaIdOrderByCreatedAtDesc(abierta.get().getId());
        }
        return List.of();
    }

    public Map<String, Object> arqueo() {
        Optional<Caja> optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (optCaja.isEmpty()) {
            optCaja = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("pendiente_cierre");
        }
        if (optCaja.isEmpty()) {
            throw new RuntimeException("No hay caja abierta");
        }
        Caja caja = optCaja.get();

        List<MovimientoCaja> movs = movRepo.findByCajaIdOrderByCreatedAtDesc(caja.getId());
        List<Venta> ventas = ventaRepo.findByFechaGreaterThanEqualOrderByFechaDesc(caja.getFechaApertura());

        BigDecimal apertura = BigDecimal.ZERO, ingresos = BigDecimal.ZERO;
        BigDecimal ventasTotal = BigDecimal.ZERO, retiros = BigDecimal.ZERO;
        for (MovimientoCaja m : movs) {
            switch (m.getTipo()) {
                case "apertura" -> apertura = apertura.add(m.getMonto());
                case "ingreso" -> ingresos = ingresos.add(m.getMonto());
                case "venta" -> ventasTotal = ventasTotal.add(m.getMonto());
                case "retiro", "gasto" -> retiros = retiros.add(m.getMonto());
            }
        }

        Map<String, Map<String, Object>> porMedio = new LinkedHashMap<>();
        for (String medio : List.of("efectivo", "debito", "credito", "transferencia", "qr")) {
            List<Venta> filtro = ventas.stream().filter(v -> v.getMedioPago().equals(medio)).toList();
            BigDecimal total = filtro.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            porMedio.put(medio, Map.of("count", filtro.size(), "total", total));
        }

        BigDecimal montoSistema = apertura.add(ventasTotal).add(ingresos).subtract(retiros);

        return Map.of(
                "caja", Map.of("id", caja.getId(), "montoApertura", apertura, "montoSistema", montoSistema,
                        "usuarioApertura", caja.getUsuarioApertura(), "fechaApertura", caja.getFechaApertura()),
                "resumen", Map.of("apertura", apertura, "ventas", ventasTotal, "ingresos", ingresos, "retiros", retiros),
                "ventasPorMedio", porMedio,
                "cantidadVentas", ventas.size(),
                "itemsVendidos", ventas.stream().mapToInt(v ->
                        v.getItems().stream().mapToInt(Venta.VentaItem::getCantidad).sum()).sum()
        );
    }

    public List<Caja> historial() {
        return cajaRepo.findAll().stream()
                .sorted(Comparator.comparing(Caja::getCreatedAt).reversed()).toList();
    }

    public BigDecimal calcularMontoSistema(Caja caja) {
        List<MovimientoCaja> movs = movRepo.findByCajaIdOrderByCreatedAtDesc(caja.getId());
        BigDecimal monto = caja.getMontoApertura();
        for (MovimientoCaja m : movs) {
            switch (m.getTipo()) {
                case "apertura", "ingreso", "venta" -> monto = monto.add(m.getMonto());
                case "retiro", "gasto", "cierre" -> monto = monto.subtract(m.getMonto());
            }
        }
        return monto;
    }
}