package ru.nsu.fit.crackhash.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrackRequestDTO {
    private String hash;
    private int maxLength;
}
