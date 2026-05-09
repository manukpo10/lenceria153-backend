package com.merceria153.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ItemRequest {
    @NotNull private String productoId;
    @NotNull private BigDecimal cantidad;
}