package com.inventario.auth.config;

import com.inventario.auth.entity.RolUsuario;
import com.inventario.auth.entity.Usuario;
import com.inventario.auth.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga los usuarios iniciales del sistema si la base de datos esta vacia.
 * admin / admin123  (rol ADMIN)
 * operador / operador123 (rol OPERADOR)
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataSeeder(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        if (!usuarioRepository.existsByUsername("admin")) {
            usuarioRepository.save(Usuario.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nombreCompleto("Administrador del Sistema")
                    .email("admin@inventario.local")
                    .rol(RolUsuario.ADMIN)
                    .activo(true)
                    .build());
        }
        if (!usuarioRepository.existsByUsername("operador")) {
            usuarioRepository.save(Usuario.builder()
                    .username("operador")
                    .password(passwordEncoder.encode("operador123"))
                    .nombreCompleto("Operador de Bodega")
                    .email("operador@inventario.local")
                    .rol(RolUsuario.OPERADOR)
                    .activo(true)
                    .build());
        }
    }
}
