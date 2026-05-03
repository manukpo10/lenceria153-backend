package com.merceria153.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ItemRequest {
    @NotNull private String productoId;
    @NotNull private Integer cantidad;
}