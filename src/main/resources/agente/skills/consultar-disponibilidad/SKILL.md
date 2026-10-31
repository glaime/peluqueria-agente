---
name: consultar-disponibilidad
description: >-
  Calcula los horarios libres de uno o de todos los peluqueros en una fecha. Es de solo lectura.
type: deterministic
input:
  fecha: Obligatoria. Fecha validada (YYYY-MM-DD) dentro de la ventana de 14 días.
  peluquero: Opcional. Nombre de un peluquero existente; si falta, se consultan todos.
output: >-
  Por cada peluquero, la lista de horarios libres (HH:MM) en orden cronológico.
---

# Rol
Informar qué horarios quedan libres para sacar turno.

# Objetivo
Responder preguntas como "¿qué horarios tiene Mateo el sábado?" y verificar, antes de pedir
confirmación, que el horario que eligió el cliente siga libre.

# Entradas
- **fecha** (obligatoria): fecha ya validada por el agente.
- **peluquero** (opcional): nombre de un peluquero existente.

# Salida
Para cada peluquero: `{ peluquero, fecha, horariosLibres: [HH:MM, ...] }`.

# Reglas
1. Horarios de turno: de martes a sábado, de 09:00 a 17:30, cada 30 minutos.
2. Se excluyen los horarios con un turno en estado RESERVADO.
3. Si la fecha es hoy, se excluyen los horarios que ya pasaron.
4. Domingos y lunes devuelven una lista vacía.

# Restricciones
- Solo lectura: no crea, modifica ni elimina turnos.
- No expone datos de otros clientes: solo informa qué horarios están libres, no quién reservó los ocupados.

# Implementación
`ConsultarDisponibilidadSkill` → `ReglasAgenda` (horarios de atención) + `TurnoService.listarReservadosPorFecha()`.
