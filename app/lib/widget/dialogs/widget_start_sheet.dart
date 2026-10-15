import 'package:flutter/material.dart';
import 'package:localsend_app/pages/home_page.dart';
import 'package:localsend_app/widget/big_button.dart';
import 'package:localsend_app/widget/dialogs/custom_bottom_sheet.dart';
import 'package:routerino/routerino.dart';

/// Ventana que aparece al tocar el widget: pregunta si se quiere recibir o enviar.
/// Usa los mismos botones e iconos que la barra de pestañas de LocalSend.
class WidgetStartSheet extends StatelessWidget {
  const WidgetStartSheet();

  /// Devuelve la pestaña elegida, o null si se cerró la ventana sin elegir.
  static Future<HomeTab?> open(BuildContext context) async {
    final dynamic result = await context.pushBottomSheet(() => const WidgetStartSheet());
    if (result == HomeTab.receive) {
      return HomeTab.receive;
    }
    if (result == HomeTab.send) {
      return HomeTab.send;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return CustomBottomSheet(
      title: 'LocalSend',
      description: null,
      child: Wrap(
        spacing: 15,
        runSpacing: 15,
        children: [
          BigButton(
            icon: HomeTab.receive.icon,
            label: HomeTab.receive.label,
            filled: true,
            onTap: () => context.pop(HomeTab.receive),
          ),
          BigButton(
            icon: HomeTab.send.icon,
            label: HomeTab.send.label,
            filled: true,
            onTap: () => context.pop(HomeTab.send),
          ),
        ],
      ),
    );
  }
}
