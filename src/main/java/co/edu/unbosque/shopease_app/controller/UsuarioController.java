package co.edu.unbosque.shopease_app.controller;


import co.edu.unbosque.shopease_app.model.CodigoModel;
import co.edu.unbosque.shopease_app.model.UsuarioModel;
import co.edu.unbosque.shopease_app.service.CodigoService;
import co.edu.unbosque.shopease_app.service.EmailService;
import co.edu.unbosque.shopease_app.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Transactional
@CrossOrigin(origins = { "http://localhost:8090", "http://localhost:8080", "*" })
@RestController
@RequestMapping("/usuario")
public class UsuarioController {

	@Autowired
	private  UsuarioService usuarioService;

	@Autowired
	private CodigoService codigoService;

	@Autowired
	private  PasswordEncoder passwordEncoder;


	@Autowired
	private EmailService emailService;



	@PostMapping("/registrar")
	@Operation(summary = "Agregar Usuarios", description = "Agrega el objeto users")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Usuario guardado con éxito"),
			@ApiResponse(responseCode = "500", description = "Error al guardar el usuarios")
	})
	public ResponseEntity<String> guardarUsuario(@RequestBody UsuarioModel usuario) {
		try {
			// Cifrar la contraseña
			String encryptedPassword = passwordEncoder.encode(usuario.getContraseña());
			usuario.setContraseña(encryptedPassword);

			usuarioService.saveUsuario(usuario);
			emailService.enviarCorreo(usuario.getEmail(),"Registro ShopEase","¡Hola "+usuario.getNombre()+"!"+"\n"
					+"Gracias por registrarse en ShopEase. Ahora puedes acceder a nuestro catálogo y disfrutar de las mejores ofertas.\n"+
					"Si tienes alguna duda o necesitas asistencia, no dudes en contactarnos.\n"+
					"¡Gracias por confiar en nosotros! \n"+
					"Atentamente.\n El equipo de ShopEase");
			return ResponseEntity.ok("Usuario guardado con éxito");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("No se insertó el Usuario: " + usuario);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error al guardar el usuario: " + e.getMessage());
		}
	}
	@GetMapping("/listar")
	@Operation(summary = "Obtener lista de usuarios ", description = "Obtener lista de usuarios")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Usuarios encontradas"),
			@ApiResponse(responseCode = "404", description = "Usuarios no encontradas")
	})
	public ResponseEntity<List<UsuarioModel>> listarTodosUsuarios() {
		List <UsuarioModel> usuarios = usuarioService.findAll();
		if (usuarios != null) {
			return ResponseEntity.ok(usuarios);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping("/iniciarSesion")
	@Operation(summary = "Iniciar sesión en la tienda", description = "Iniciar sesión en ShopEase")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Inicio de sesión exitoso"),
			@ApiResponse(responseCode = "400", description = "Faltan credenciales"),
			@ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
	})
	public ResponseEntity<String> iniciarSesion(@RequestBody UsuarioLoginRequest loginRequest) {
		if (loginRequest.getEmail() == null || loginRequest.getEmail().isEmpty() ||
				loginRequest.getContraseña() == null || loginRequest.getContraseña().isEmpty()) {
			return ResponseEntity.badRequest().body("El email o la contraseña no pueden estar vacíos");
		}


		UsuarioModel usuario = usuarioService.findByEmail(loginRequest.getEmail());
		if (usuario == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("El usuario no fue encontrado");
		}


		if (passwordEncoder.matches(loginRequest.getContraseña(), usuario.getContraseña())) {
			return ResponseEntity.ok("Inicio de sesión exitoso");
		} else {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email y/o contraseña incorrectos");
		}
	}


	@PostMapping("/solicitar-codigo")
	@Operation(summary = "Solicitar código de cambio de contraseña", description = "Genera y envía un código al correo del usuario para el cambio de contraseña")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Código enviado al correo"),
			@ApiResponse(responseCode = "404", description = "Usuario no encontrado")
	})
	public ResponseEntity<String> solicitarCodigo(@RequestBody String email) {
		System.out.println("Email recibido: " + email); 
		UsuarioModel usuario = usuarioService.findByEmail(email);
		if (usuario == null) {
			System.out.println("Usuario no encontrado para el email: " + email);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
		}

		String codigo = UUID.randomUUID().toString().substring(0, 6); 

		CodigoModel codigoModel = new CodigoModel();
		codigoModel.setIdUsuario(usuario.getId_usuario());
		codigoModel.setCodigo(codigo);
		codigoService.saveCodigo(codigoModel);

		emailService.enviarCorreo(usuario.getEmail(), "Solicitud cambio de contraseña ShopEase", 
				"¡Hola " + usuario.getNombre() + "!\n" +
						"Hemos recibido tu solicitud de cambio de contraseña.\n" +
						"Para restablecer tu contraseña ingresa el siguiente código.\n" +
						codigo + "\n" +
						"¡Si no has sido tú, contáctanos de inmediato.\n" +
				"Atentamente,\nEl equipo de ShopEase");
		return ResponseEntity.ok("Código enviado al correo");
	}


	@PostMapping("/validar-codigo")
	@Operation(summary = "Validar código de cambio de contraseña", description = "Valida el código enviado al correo para el cambio de contraseña")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Código válido, puede proceder a cambiar la contraseña"),
			@ApiResponse(responseCode = "401", description = "Código inválido, intente nuevamente")
	})
	public ResponseEntity<String> validarCodigo(@RequestBody CodigoModel codigoRequest) {
		CodigoModel codigoAlmacenado = codigoService.findById(codigoRequest.getIdUsuario());

		if (codigoAlmacenado == null || !codigoAlmacenado.getCodigo().equals(codigoRequest.getCodigo())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Código inválido. Por favor, intenta de nuevo.");
		}

		return ResponseEntity.ok("Código válido. Puedes proceder a cambiar tu contraseña.");
	}


	@PostMapping("/cambiar-contrasena-por-email")
	@Operation(summary = "Cambiar contraseña por email", description = "Cambia la contraseña de un usuario buscando por su email")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Contraseña cambiada exitosamente"),
			@ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
			@ApiResponse(responseCode = "400", description = "Nueva contraseña no válida")
	})
	public ResponseEntity<String> cambiarContrasenaPorEmail(@RequestBody String contraseña, String email) {

		UsuarioModel usuario = usuarioService.findByEmail(email);

		if (usuario == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
		}

		if (contraseña == null || contraseña.isEmpty()) {
			return ResponseEntity.badRequest().body("La nueva contraseña no puede estar vacía");
		}

		String nuevaContrasenaCifrada = passwordEncoder.encode(contraseña);
		usuario.setContraseña(nuevaContrasenaCifrada);
		usuarioService.saveUsuario(usuario);
		int id = usuario.getId_usuario();	
		codigoService.deleteByIdUsuario(id);

		return ResponseEntity.ok("Contraseña cambiada exitosamente");
	}


}
