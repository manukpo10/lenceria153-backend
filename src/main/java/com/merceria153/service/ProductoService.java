package com.merceria153.service;

import com.merceria153.model.Producto;
import com.merceria153.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProductoService {

    private final ProductoRepository repo;

    public ProductoService(ProductoRepository repo) {
        this.repo = repo;
    }

    public List<Producto> listar(String q, String rubro, Boolean activo) {
        List<Producto> all = activo == null || activo
            ? repo.findByActivoTrue()
            : repo.findByActivoFalse();
        if (rubro != null && !rubro.equals("TODOS")) {
            all = all.stream().filter(p -> p.getRubro().equals(rubro)).toList();
        }
        if (q != null && !q.isBlank()) {
            String term = q.toLowerCase();
            all = all.stream().filter(p ->
                p.getCodigo().contains(term) ||
                p.getDescripcion().toLowerCase().contains(term)
            ).toList();
        }
        return all.stream().limit(100).toList();
    }

    public Producto obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    public List<String> listarRubros() {
        return repo.findByActivoTrue().stream()
                .map(Producto::getRubro)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public Producto crear(Producto data) {
        if (repo.existsByCodigo(data.getCodigo())) {
            throw new RuntimeException("Ya existe un producto con ese código");
        }
        data.setId(null);
        data.setActivo(true);
        data.setCreatedAt(LocalDateTime.now());
        return repo.save(data);
    }

    @Transactional
    public Producto actualizar(String id, Producto data) {
        Producto p = obtener(id);
        System.out.println(">>> actualizar precioVenta incoming: " + data.getPrecioVenta());
        p.setDescripcion(data.getDescripcion());
        p.setRubro(data.getRubro());
        p.setCosto(data.getCosto());
        p.setPrecio(data.getPrecio());
        p.setPrecioUnidad(data.getPrecioUnidad());
        p.setPrecioVenta(data.getPrecioVenta());
        p.setPack(data.getPack());
        p.setStock(data.getStock());
        p.setActivo(data.getActivo());
        p.setUpdatedAt(LocalDateTime.now());
        System.out.println(">>> after set, precioVenta=" + p.getPrecioVenta());
        return repo.save(p);
    }

    @Transactional
    public void eliminar(String id) {
        Producto p = obtener(id);
        p.setActivo(false);
        p.setUpdatedAt(LocalDateTime.now());
        repo.save(p);
    }

    @Transactional
    public int cleanupInactivos(int dias) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(dias);
        List<Producto> toDelete = repo.findAll().stream()
            .filter(p -> !p.getActivo() && p.getUpdatedAt() != null && p.getUpdatedAt().isBefore(cutoff))
            .toList();
        repo.deleteAll(toDelete);
        return toDelete.size();
    }

    @Transactional
    public Producto setStock(String id, int stock) {
        Producto p = obtener(id);
        p.setStock(Math.max(0, stock));
        p.setUpdatedAt(LocalDateTime.now());
        return repo.save(p);
    }

    @Transactional
    public int resetearStock(int stock) {
        List<Producto> all = repo.findByActivoTrue();
        for (Producto p : all) {
            p.setStock(stock);
            p.setUpdatedAt(LocalDateTime.now());
            repo.save(p);
        }
        return all.size();
    }

    public String exportarStockCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("sep=,\n");
        sb.append("codigo,descripcion,stock\n");
        for (Producto p : repo.findByActivoTrue()) {
            String desc = p.getDescripcion() == null ? "" : p.getDescripcion().replace("\"", "\"\"");
            sb.append(p.getCodigo()).append(",\"").append(desc).append("\",")
              .append(p.getStock() == null ? 0 : p.getStock()).append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public Map<String, Object> importarStockCsv(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) content = content.substring(1);
        String[] lineas = content.split("\\r?\\n");

        char sep = ',';
        if (lineas.length > 0 && lineas[0].toLowerCase().startsWith("sep=") && lineas[0].length() >= 5) {
            sep = lineas[0].charAt(4);
        } else {
            int comas = 0, puntoComas = 0, semis = 0;
            for (int i = 0; i < Math.min(lineas.length, 5); i++) {
                for (char c : lineas[i].toCharArray()) {
                    if (c == ',') comas++;
                    else if (c == ';') puntoComas++;
                    else if (c == '\t') semis++;
                }
            }
            if (puntoComas > comas && puntoComas > semis) sep = ';';
            else if (semis > comas && semis > puntoComas) sep = '\t';
        }

        int updated = 0, notFound = 0, invalid = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isEmpty()) continue;
            if (linea.toLowerCase().startsWith("sep=")) continue;
            if (i <= 1 && linea.toLowerCase().contains("codigo")) continue;

            java.util.List<String> campos = parsearLineaCsv(linea, sep);
            if (campos.size() < 2) { invalid++; continue; }
            String codigo = campos.get(0).trim();
            String stockStr = campos.get(campos.size() - 1).trim();

            if (codigo.contains(",") && codigo.contains("\"") == false && sep != ',') {
                // shape: campo[0] = "codigo,descripcion,0" pegoteado por Excel
                java.util.List<String> sub = parsearLineaCsv(codigo, ',');
                if (sub.size() >= 1) codigo = sub.get(0).trim();
            } else if (codigo.contains(",")) {
                java.util.List<String> sub = parsearLineaCsv(codigo, ',');
                if (sub.size() >= 1) codigo = sub.get(0).trim();
            }
            if (i == 0 && codigo.equalsIgnoreCase("Column1")) continue;
            int stock;
            try { stock = Integer.parseInt(stockStr); } catch (NumberFormatException e) { invalid++; continue; }
            if (stock < 0) stock = 0;

            Optional<Producto> existing = repo.findByCodigo(codigo);
            if (existing.isPresent()) {
                Producto prod = existing.get();
                prod.setStock(stock);
                prod.setUpdatedAt(LocalDateTime.now());
                repo.save(prod);
                updated++;
            } else {
                notFound++;
            }
        }
        return Map.of("updated", updated, "notFound", notFound, "invalid", invalid);
    }

    private java.util.List<String> parsearLineaCsv(String linea, char sep) {
        java.util.List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (enComillas) {
                if (c == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        enComillas = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == sep) { out.add(cur.toString()); cur.setLength(0); }
                else if (c == '"') { enComillas = true; }
                else { cur.append(c); }
            }
        }
        out.add(cur.toString());
        return out;
    }

    @Transactional
    public void decrementarStock(String productoId, int cantidad) {
        Producto p = obtener(productoId);
        p.setStock(p.getStock() - cantidad);
        repo.save(p);
    }

    @Transactional
    public Map<String, Object> calcularPreciosVenta(BigDecimal porcentaje) {
        List<Producto> all = repo.findByActivoTrue();
        int actualizados = 0;
        for (Producto p : all) {
            if (p.getPrecio() != null && p.getPrecio().signum() > 0) {
                BigDecimal venta = p.getPrecio().multiply(BigDecimal.ONE.add(porcentaje.divide(BigDecimal.valueOf(100))));
                p.setPrecioVenta(venta);
                int pack = p.getPack() != null && p.getPack() > 0 ? p.getPack() : 1;
                p.setPrecioUnidadVenta(venta.divide(BigDecimal.valueOf(pack), 2, java.math.RoundingMode.HALF_UP));
                p.setUpdatedAt(LocalDateTime.now());
                repo.save(p);
                actualizados++;
            }
        }
        return Map.of("actualizados", actualizados, "porcentaje", porcentaje);
    }

    @Transactional
    public int seedDesdeJson(List<Map<String, Object>> productosJson) {
        List<Producto> productos = new ArrayList<>();
        for (Map<String, Object> p : productosJson) {
            Producto prod = new Producto();
            prod.setCodigo(String.valueOf(p.get("codigo")));
            prod.setDescripcion((String) p.get("descripcion"));
            prod.setRubro((String) p.get("rubro"));
            Object costo = p.get("costo");
            prod.setCosto(costo != null ? new BigDecimal(costo.toString()) : null);
            prod.setPrecio(new BigDecimal(p.get("precio").toString()));
            Object pu = p.get("precioUnidad");
            prod.setPrecioUnidad(pu != null ? new BigDecimal(pu.toString()) : null);
            Object pv = p.get("precioVenta");
            prod.setPrecioVenta(pv != null ? new BigDecimal(pv.toString()) : null);
            Object pack = p.get("pack");
            prod.setPack(pack != null ? ((Number) pack).intValue() : 1);
            Object unidad = p.get("unidad");
            prod.setUnidad(unidad != null ? ((Number) unidad).intValue() : 1);
            Object stock = p.get("stock");
            prod.setStock(stock != null ? ((Number) stock).intValue() : 0);
            prod.setActivo(true);
            prod.setCreatedAt(LocalDateTime.now());
            productos.add(prod);
        }
        repo.saveAll(productos);
        return productos.size();
    }

    public Map<String, Object> importarDesdePdf(org.springframework.web.multipart.MultipartFile file) {
        System.out.println(">>> IMPORTAR PDF START - filename: " + file.getOriginalFilename() + " size: " + file.getSize());
        List<Map<String, Object>> productos = new ArrayList<>();
        String texto = "";
        try {
            byte[] pdfBytes = file.getInputStream().readAllBytes();
            System.out.println("PDF bytes leidos: " + pdfBytes.length);
            org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            texto = stripper.getText(document);
            document.close();
            System.out.println("=== PDF DEBUG: " + texto.length() + " chars ===");
            System.out.println("PRIMERAS 2000: " + texto.substring(0, Math.min(2000, texto.length())));
        } catch (Exception e) {
            System.out.println("ERROR PDF: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error leyendo PDF: " + e.getMessage());
        }

        java.util.regex.Pattern asteriscoRe = java.util.regex.Pattern.compile(
            "\\*(\\d+)\\s+(.+?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
        java.util.regex.Pattern prodSegmentRe = java.util.regex.Pattern.compile(
            "\\*(\\d+)\\s+(.+?)\\s+(\\d+(?:\\.\\d{3})*,\\d{2})");
        java.util.regex.Pattern dolarRe = java.util.regex.Pattern.compile(
            "\\$\\s*([\\d.]+(?:,\\d{1,2})?)");
        java.util.regex.Pattern codigoLineaRe = java.util.regex.Pattern.compile("^(\\d{3,8})\\s+(.+)$");
        java.util.Set<String> codigosUsados = new java.util.HashSet<>();
        String rubroActual = "OTROS";

        String[] lineas = texto.split("\\r?\\n");
        for (String original : lineas) {
            String linea = original.replaceAll("\\s+", " ").trim();
            if (linea.isEmpty()) continue;
            if (esEncabezadoListaPrecios(linea)) continue;

            java.util.regex.Matcher ma = asteriscoRe.matcher(linea);
            boolean huboMatchAsterisco = false;
            while (ma.find()) {
                huboMatchAsterisco = true;
                String codigo = ma.group(1);
                String descripcion = ma.group(2).trim();
                if (descripcion.isEmpty()) continue;
                BigDecimal precio = parsePrecioAr(ma.group(3));
                if (precio == null || precio.signum() <= 0) continue;
                if (codigosUsados.contains(codigo)) continue;
                codigosUsados.add(codigo);

                String rubro = inferirRubro(descripcion);
                if ("OTROS".equals(rubro)) rubro = rubroActual;

                int pack = detectarPack(descripcion);
                BigDecimal precioUnidad = precio.divide(BigDecimal.valueOf(pack), 2, java.math.RoundingMode.HALF_UP);

                Map<String, Object> prod = new HashMap<>();
                prod.put("codigo", codigo);
                prod.put("descripcion", descripcion);
                prod.put("precio", precio);
                prod.put("pack", pack);
                prod.put("precioUnidadLista", precioUnidad);
                prod.put("precioUnidadVenta", precioUnidad);
                prod.put("stock", 999);
                prod.put("rubro", rubro);
                productos.add(prod);
            }
            if (!huboMatchAsterisco) {
                java.util.regex.Matcher ms = prodSegmentRe.matcher(linea);
                while (ms.find()) {
                    String codigo = ms.group(1);
                    String descripcion = ms.group(2).trim();
                    if (descripcion.isEmpty()) continue;
                    BigDecimal precio = parsePrecioAr(ms.group(3));
                    if (precio == null || precio.signum() <= 0) continue;
                    if (codigosUsados.contains(codigo)) continue;
                    codigosUsados.add(codigo);

                    String rubro = inferirRubro(descripcion);
                    if ("OTROS".equals(rubro)) rubro = rubroActual;

                    int pack = detectarPack(descripcion);
                    BigDecimal precioUnidad = precio.divide(BigDecimal.valueOf(pack), 2, java.math.RoundingMode.HALF_UP);

                    Map<String, Object> prod = new HashMap<>();
                    prod.put("codigo", codigo);
                    prod.put("descripcion", descripcion);
                    prod.put("precio", precio);
                    prod.put("pack", pack);
                    prod.put("precioUnidadLista", precioUnidad);
                    prod.put("precioUnidadVenta", precioUnidad);
                    prod.put("stock", 999);
                    prod.put("rubro", rubro);
                    productos.add(prod);
                }
            }
            if (huboMatchAsterisco) continue;

            java.util.regex.Matcher md = dolarRe.matcher(linea);
            java.util.List<BigDecimal> precios = new ArrayList<>();
            int firstPriceIdx = -1;
            while (md.find()) {
                if (firstPriceIdx < 0) firstPriceIdx = md.start();
                BigDecimal p = parsePrecioAr(md.group(1));
                if (p != null) precios.add(p);
            }

            if (precios.isEmpty()) {
                String posibleRubro = posibleEncabezadoRubro(linea);
                if (posibleRubro != null) rubroActual = posibleRubro;
                continue;
            }

            String descripcion = linea.substring(0, firstPriceIdx).trim();
            String codigoExplicito = null;
            java.util.regex.Matcher cm = codigoLineaRe.matcher(descripcion);
            if (cm.matches()) {
                codigoExplicito = cm.group(1);
                descripcion = cm.group(2).trim();
            }
            if (esLineaResumen(descripcion)) continue;
            if (descripcion.length() < 2) continue;

            BigDecimal precio = null;
            for (BigDecimal p : precios) {
                if (p.compareTo(BigDecimal.ZERO) > 0) { precio = p; break; }
            }
            if (precio == null) continue;

            String codigo = codigoExplicito != null
                ? codigoExplicito
                : generarCodigoDesde(descripcion, precio, codigosUsados);
            codigosUsados.add(codigo);

            String rubro = inferirRubro(descripcion);
            if ("OTROS".equals(rubro)) rubro = rubroActual;

            int pack = detectarPack(descripcion);
            BigDecimal precioUnidad = precio.divide(BigDecimal.valueOf(pack), 2, java.math.RoundingMode.HALF_UP);

            Map<String, Object> prod = new HashMap<>();
            prod.put("codigo", codigo);
            prod.put("descripcion", descripcion);
            prod.put("precio", precio);
            prod.put("pack", pack);
            prod.put("precioUnidadLista", precioUnidad);
            prod.put("precioUnidadVenta", precioUnidad);
            prod.put("stock", 999);
            prod.put("rubro", rubro);
            productos.add(prod);
        }

        String textoDebug = texto.isEmpty() ? "[PDF VACIO O NO LEIDO]" : (texto.length() > 1000 ? texto.substring(0, 1000) : texto);
        System.out.println("=== PDF TEXTO EXTRAIDO (" + textoDebug.length() + " chars) ===");
        System.out.println(textoDebug);
        System.out.println("=== FIN PDF TEXTO ===");
        System.out.println("Productos detectados: " + productos.size());

        return Map.of(
                "productos", productos,
                "total", productos.size(),
                "debug_texto", textoDebug
            );
    }

    private BigDecimal parsePrecioAr(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        boolean tieneComa = s.contains(",");
        if (tieneComa) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            s = s.replace(".", "");
        }
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private boolean esEncabezadoListaPrecios(String linea) {
        String l = linea.toLowerCase();
        return l.startsWith("cj sa") || l.startsWith("mygestion") ||
               l.startsWith("lista de precios") || l.startsWith("producto:") ||
               l.startsWith("precios finales") || l.startsWith("codigo descripcion") ||
               l.startsWith("código descripción") || l.startsWith("página") ||
               l.startsWith("pagina") || l.startsWith("ref. contable") ||
               l.startsWith("fecha:");
    }

    private static final java.util.Set<String> PALABRAS_RESUMEN = java.util.Set.of(
        "total", "subtotal", "subtotal nacional", "subtotal plantas",
        "pronto pago", "embalajes envios / costos", "embalajes",
        "lista", "c/desc", "off", "unidad", "tamaño", "tamano",
        "descripcion", "descripción", "codigo", "código", "novedades",
        "productos", "pedido", "orden", "x", "n"
    );

    private boolean esLineaResumen(String desc) {
        String d = desc.toLowerCase().trim();
        if (d.isEmpty()) return true;
        if (PALABRAS_RESUMEN.contains(d)) return true;
        if (d.startsWith("ir a ") || d.startsWith("espacio para") || d.startsWith("clik")) return true;
        if (d.matches("[\\d.,\\s$]+")) return true;
        return false;
    }

    private String posibleEncabezadoRubro(String linea) {
        if (linea.length() > 80) return null;
        if (linea.contains("*")) return null;
        if (linea.matches(".*\\d+,\\d{2}.*")) return null;
        String l = linea.replaceAll("[¡!:]+$", "").trim();
        if (l.isEmpty()) return null;
        if (esLineaResumen(l)) return null;
        if (l.length() < 4) return null;
        if (!l.toLowerCase().matches(".*[a-záéíóúñ].*")) return null;
        return l.toUpperCase();
    }

    private String generarCodigoDesde(String desc, BigDecimal precio, java.util.Set<String> usados) {
        String base = (desc + "|" + precio.toPlainString()).toLowerCase();
        String hex = Integer.toHexString(base.hashCode()).toUpperCase();
        if (hex.startsWith("-")) hex = "N" + hex.substring(1);
        String codigo = "PDF-" + hex;
        int n = 1;
        String candidato = codigo;
        while (usados.contains(candidato)) {
            candidato = codigo + "-" + (++n);
        }
        return candidato;
    }

    private int detectarPack(String desc) {
        String d = desc.toUpperCase();
        java.util.regex.Pattern xp = java.util.regex.Pattern.compile("(?:^|[^A-Z0-9])X\\s*(\\d{1,4})(?:\\s|$|\\b(?!(?:MT|MTS|METRO|CM|MM|UN|UNI|UNID|PIEZA|PAQUETE)))");
        java.util.regex.Matcher m = xp.matcher(d);
        java.util.List<Integer> candidatos = new java.util.ArrayList<>();
        while (m.find()) {
            candidatos.add(Integer.parseInt(m.group(1)));
        }
        if (!candidatos.isEmpty()) return candidatos.get(candidatos.size() - 1);

        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?:^|\\s)X\\s*(\\d{1,4})(?:\\s|$)").matcher(d);
        if (m2.find()) {
            int n = Integer.parseInt(m2.group(1));
            return n > 0 && n <= 10000 ? n : 1;
        }
        return 1;
    }

    private String inferirRubro(String desc) {
        String d = desc.toUpperCase();
        if (d.contains("HILO") || d.contains("ALGODON")) return "HILOS";
        if (d.contains("CIERRE") || d.contains("ZIP")) return "CIERRES";
        if (d.contains("BOTON")) return "BOTONES";
        if (d.contains("ELASTIC")) return "ELASTICOS";
        if (d.contains("CINTA") || d.contains("FRANELA")) return "CINTAS";
        if (d.contains("ALAMBRE")) return "ALAMBRES";
        if (d.contains("AGUJA")) return "AGUJAS";
        if (d.contains("ABROJO") || d.contains("VELCRO")) return "ABROJOS";
        if (d.contains("BIA") || d.startsWith("BIES")) return "BIES";
        if (d.contains("LANA")) return "LANAS";
        if (d.contains("ALAMAR")) return "ALAMARES";
        if (d.startsWith("ALF")) return "ALFILERES";
        if (d.startsWith("APL ") || d.contains("APLIQUE")) return "APLIQUES";
        if (d.startsWith("ACC ") || d.contains("ACCESORIO")) return "ACCESORIOS";
        if (d.contains("ACEITE")) return "ACEITES";
        return "OTROS";
    }

    @Transactional
    public Map<String, Object> confirmarImportacion(List<Map<String, Object>> productosJson, String modo) {
        if (modo == null) modo = "all";
        boolean permiteCrear = modo.equals("all") || modo.equals("create_only");
        boolean permiteActualizar = modo.equals("all") || modo.equals("update_only") || modo.equals("update_precio");
        boolean soloPrecio = modo.equals("update_precio");

        int created = 0, updated = 0, skipped = 0;
        for (Map<String, Object> p : productosJson) {
            String codigo = String.valueOf(p.get("codigo"));
            Optional<Producto> existing = repo.findByCodigo(codigo);
            if (existing.isPresent()) {
                if (!permiteActualizar) { skipped++; continue; }
                Producto prod = existing.get();
                Object prec = p.get("precio");
                if (prec != null) prod.setPrecio(new BigDecimal(prec.toString()));
                Object pUnid = p.get("precioUnidad");
                if (pUnid != null) prod.setPrecioUnidad(new BigDecimal(pUnid.toString()));
                Object pUnidLista = p.get("precioUnidadLista");
                if (pUnidLista != null) prod.setPrecioUnidadLista(new BigDecimal(pUnidLista.toString()));
                Object pUnidVenta = p.get("precioUnidadVenta");
                if (pUnidVenta != null) prod.setPrecioUnidadVenta(new BigDecimal(pUnidVenta.toString()));
                if (!soloPrecio) {
                    String desc = (String) p.get("descripcion");
                    if (desc != null) prod.setDescripcion(desc);
                    String rub = (String) p.get("rubro");
                    if (rub != null) prod.setRubro(rub);
                    Object cost = p.get("costo");
                    if (cost != null) prod.setCosto(new BigDecimal(cost.toString()));
                    prod.setStock(999);
                    Object pk = p.get("pack");
                    if (pk != null) prod.setPack(((Number) pk).intValue());
                } else {
                    prod.setStock(999);
                }
                prod.setActivo(true);
                prod.setUpdatedAt(LocalDateTime.now());
                repo.save(prod);
                updated++;
            } else {
                if (!permiteCrear) { skipped++; continue; }
                Producto prod = new Producto();
                prod.setCodigo(codigo);
                prod.setDescripcion((String) p.get("descripcion"));
                prod.setRubro((String) p.get("rubro"));
                Object prec = p.get("precio");
                prod.setPrecio(prec != null ? new BigDecimal(prec.toString()) : BigDecimal.ZERO);
                Object pUnid = p.get("precioUnidad");
                if (pUnid != null) prod.setPrecioUnidad(new BigDecimal(pUnid.toString()));
                Object pUnidLista = p.get("precioUnidadLista");
                if (pUnidLista != null) prod.setPrecioUnidadLista(new BigDecimal(pUnidLista.toString()));
                Object pUnidVenta = p.get("precioUnidadVenta");
                if (pUnidVenta != null) prod.setPrecioUnidadVenta(new BigDecimal(pUnidVenta.toString()));
                Object cost = p.get("costo");
                prod.setCosto(cost != null ? new BigDecimal(cost.toString()) : null);
                prod.setStock(999);
                Object pk = p.get("pack");
                prod.setPack(pk != null ? ((Number) pk).intValue() : 1);
                prod.setActivo(true);
                prod.setCreatedAt(LocalDateTime.now());
                repo.save(prod);
                created++;
            }
        }
        return Map.of("created", created, "updated", updated, "skipped", skipped);
    }
}