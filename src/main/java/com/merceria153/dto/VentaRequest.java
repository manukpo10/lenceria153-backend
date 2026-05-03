package com.merceria153.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VentaRequest {
    private List<ItemRequest> items;
    private String medioPago;
    private BigDecimal descuento;
}