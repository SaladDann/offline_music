import 'package:flutter/material.dart';

/// Modos de reproducción soportados por la cola.
enum PlayMode {
  order(
    icon: Icons.repeat_outlined,
    label: 'En orden',
    isActive: false,
  ),
  repeatAll(
    icon: Icons.repeat_outlined,
    label: 'Repetir todo',
    isActive: true,
  ),
  repeatOne(
    icon: Icons.repeat_one_outlined,
    label: 'Repetir una',
    isActive: true,
  ),
  shuffle(
    icon: Icons.shuffle_outlined,
    label: 'Aleatorio',
    isActive: true,
  );

  const PlayMode({
    required this.icon,
    required this.label,
    required this.isActive,
  });

  final IconData icon;
  final String label;
  final bool isActive;

  /// Siguiente modo en rotación circular.
  PlayMode get next => PlayMode.values[(index + 1) % PlayMode.values.length];

  /// Resuelve un [PlayMode] por su nombre o cae en [PlayMode.order].
  static PlayMode fromName(String? value) {
    if (value == null) return PlayMode.order;
    for (final mode in PlayMode.values) {
      if (mode.name == value) return mode;
    }
    return PlayMode.order;
  }
}
