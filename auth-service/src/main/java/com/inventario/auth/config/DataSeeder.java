package com.inventario.auth.config;

import com.inventario.auth.entity.RolUsuario;
import com.inventario.auth.entity.Usuario;
import com.inventario.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga los usuarios iniciales del sistema si la base de datos esta vacia. Las contrasenas
 * vienen de configuracion (variables de entorno SEED_ADMIN_PASSWORD y SEED_OPERADOR_PASSWORD
 * en produccion), nunca escritas en el codigo fuente.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String adminPassword;
    private final String operadorPassword;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      @Value("${app.seed.admin-password}") String adminPassword,
                      @Value("${app.seed.operador-password}") String operadorPassword) {
        this.usuarioRepository = usuarioRepository;
        this.adminPassword = adminPassword;
        this.operadorPassword = operadorPassword;
    }

    @Override
    public void run(String... args) {
        if (!usuarioRepository.existsByUsername("admin")) {
            usuarioRepository.save(Usuario.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(adminPassword))
                    .nombreCompleto("Administrador del Sistema")
                    .email("admin@inventario.local")
                    .rol(RolUsuario.ADMIN)
                    .activo(true)
                    .build());
        }
        if (!usuarioRepository.existsByUsername("operador")) {
            usuarioRepository.save(Usuario.builder()
                    .username("operador")
                    .password(passwordEncoder.encode(operadorPassword))
                    .nombreCompleto("Operador de Bodega")
                    .email("operador@inventario.local")
                    .rol(RolUsuario.OPERADOR)
                    .activo(true)
                    .build());
        }
    }
}
