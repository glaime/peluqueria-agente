---
name: reservar-turno
description: >-
  Crea un turno en estado RESERVADO para un peluquero, fecha y hora, después de que el cliente confirma.
  Es la única skill que escribe datos, y solo puede crear turnos.
type: deterministic
input:
  peluquero: Obligatorio. Nombre de un peluquero existente.
  fecha: Obligatoria. Fecha validada (YYYY-MM-DD), de martes a sábado, dentro de la ventana de 14 días.
  hora: Obligatoria. Hora validada (HH:MM), entre 09:00 y 17:30, en punto o y media, libre según consultar-disponibilidad.
output: >-
  Confirmada (id del turno, peluquero, fecha y hora) o Rechazada (motivo informado por la API).
---

# Rol
Registrar el turno que el cliente pidió y confirmó.

# Objetivo
Crear el turno y devolver su número para informárselo al cliente.

# Entradas
- **peluquero**, **fecha** y **hora**: todos obligatorios y ya validados por el agente.

# Precondición
El cliente confirmó explícitamente la reserva ("sí", "confirmo"). El agente nunca ejecuta esta
skill sin esa confirmación.

# Salida
- `Confirmada { turnoId, peluquero, fecha, hora }`
- `Rechazada { motivo }`: por ejemplo, si otro cliente reservó ese horario un instante antes.

# Restricciones
1. Solo crea turnos en estado RESERVADO. No existe ninguna skill para modificar o eliminar turnos
   (principio de mínimo privilegio): aunque el modelo fuera manipulado, el agente no tiene cómo borrar datos.
2. El turno se crea con `TurnoService`, que vuelve a validar las reglas de negocio y los conflictos de
   horario (defensa en profundidad): si el agente se equivocara, la API igual rechazaría el turno.
3. El agente solo informa una reserva como hecha si esta skill devolvió el id del turno.

# Implementación
`ReservarTurnoSkill` → `TurnoService.crear()`.
