package com.canalesdigitales.contactupdate.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartEmailUpdateRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        @Email(message = "El formato del correo no es válido")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "El formato del correo no es válido")
        String newEmail) {
}
