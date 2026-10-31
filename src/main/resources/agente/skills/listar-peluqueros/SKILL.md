---
name: listar-peluqueros
description: >-
  Informa quiénes atienden en la peluquería. Es de solo lectura.
type: deterministic
input: Ninguna.
output: >-
  Lista con los nombres de los peluqueros, en el orden en que están registrados.
  Ejemplo: [Tobias, Santiago, Mateo].
---

# Rol
Informar al cliente qué peluqueros atienden.

# Objetivo
Responder preguntas como "¿quiénes atienden?" o "¿con quién puedo sacar turno?".

# Entradas
Ninguna.

# Salida
Lista de nombres de peluqueros (texto). No incluye ids ni datos internos.

# Restricciones
- Solo lectura: no crea, modifica ni elimina peluqueros.
- Se ejecuta cuando `interpretar-pedido` detecta la intención LISTAR_PELUQUEROS.

# Implementación
`ListarPeluquerosSkill` → `DirectorioPeluqueros` → `PeluqueroService.listarTodos()`.
