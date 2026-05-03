package com.merceria153.service;

import com.merceria153.dto.ItemRequest;
import com.merceria153.dto.VentaRequest;
import com.merceria153.model.*;
import com.merceria153.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VentaService {

    private final VentaRepository ventaRepo;
    private final ProductoService productoSvc;
    private final MovimientoCajaRepository movRepo;
    private final CajaRepository cajaRepo;

    public VentaService(VentaRepository ventaRepo, ProductoService productoSvc,
                        MovimientoCajaRepository movRepo, CajaRepository cajaRepo) {
        this.ventaRepo = ventaRepo;
        this.productoSvc = productoSvc;
        this.movRepo = movRepo;
        this.cajaRepo = cajaRepo;
    }

    public List<Venta> listar(LocalDate desde, LocalDate hasta, String medio) {
        LocalDateTime from = desde != null ? desde.atStartOfDay() : LocalDate.of(2020, 1, 1).atStartOfDay();
        LocalDateTime to = hasta != null ? hasta.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        List<Venta> all = ventaRepo.findByFechaBetweenOrderByFechaDesc(from, to);
        if (medio != null && !medio.isBlank()) {
            all = all.stream().filter(v -> v.getMedioPago().equals(medio)).toList();
        }
        return all;
    }

    public Venta obtener(String id) {
        return ventaRepo.findById(id).orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }

    @Transactional
    public Venta crear(VentaRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getDetails().toString() : "anonimo";
        String userId = auth != null ? auth.getPrincipal().toString() : "anonimo";

        List<Venta.VentaItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemRequest itemReq : req.getItems()) {
            Producto p = productoSvc.obtener(itemReq.getProductoId());
            if (p.getStock() < itemReq.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para " + p.getDescripcion() + ". Disponible: " + p.getStock());
            }
            BigDecimal precio = p.getPrecioUnidadVenta() != null ? p.getPrecioUnidadVenta()
                : (p.getPrecioVenta() != null ? p.getPrecioVenta() : (p.getPrecioUnidad() != null ? p.getPrecioUnidad() : p.getPrecio()));
            BigDecimal itemSubtotal = precio.multiply(BigDecimal.valueOf(itemReq.getCantidad()));
            items.add(new Venta.VentaItem(p.getId(), p.getCodigo(), p.getDescripcion(),
                    itemReq.getCantidad(), precio, itemSubtotal));
            subtotal = subtotal.add(itemSubtotal);
        }

        BigDecimal descuento = req.getDescuento() != null ? req.getDescuento() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(descuento);

        Venta venta = new Venta();
        venta.setItems(items);
        venta.setSubtotal(subtotal);
        venta.setDescuento(descuento);
        venta.setTotal(total);
        venta.setMedioPago(req.getMedioPago());
        venta.setUserId(userId);
        venta.setUsuarioNombre(username);
        venta.setFecha(LocalDateTime.now());
        Long siguiente = ventaRepo.findMaxNumeroVenta() + 1;
        venta.setNumeroVenta(siguiente);
        venta = ventaRepo.save(venta);

        // decrementar stock
        for (ItemRequest itemReq : req.getItems()) {
            productoSvc.decrementarStock(itemReq.getProductoId(), itemReq.getCantidad());
        }

        // registrar movimiento en caja si es efectivo
        Optional<Caja> cajaAbierta = cajaRepo.findFirstByEstadoOrderByCreatedAtDesc("abierta");
        if (cajaAbierta.isPresent() && "efectivo".equals(req.getMedioPago())) {
            MovimientoCaja mov = new MovimientoCaja();
            mov.setCajaId(cajaAbierta.get().getId());
            mov.setTipo("venta");
            mov.setMonto(total);
            mov.setMedioPago(req.getMedioPago());
            mov.setDescripcion("Venta " + venta.getId());
            mov.setUserId(userId);
            mov.setUsuarioNombre(username);
            movRepo.save(mov);
        }

        return venta;
    }

    public Map<String, Object> stats() {
        LocalDateTime hoy = LocalDate.now().atStartOfDay();
        List<Venta> ventasHoy = ventaRepo.findByFechaGreaterThanEqualOrderByFechaDesc(hoy);

        BigDecimal totalHoy = ventasHoy.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemsHoy = ventasHoy.stream().mapToInt(v ->
                v.getItems().stream().mapToInt(Venta.VentaItem::getCantidad).sum()).sum();

        return Map.of(
                "ventasHoy", ventasHoy.size(),
                "totalHoy", totalHoy,
                "itemsHoy", itemsHoy
        );
    }
}