package com.example.trabalhocalculo3.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

// Controi a caixa de diálogo para seleção de unidade
public class UnitSelectionDialog {

    public interface OnUnitSelectedListener {
        void onUnitSelected(Unit selectedUnit, int position);
    }

    public static void show(Context context, OnUnitSelectedListener listener) {
        String[] units = {"Milímetros (mm)", "Centímetros (cm)", "Metros (m)"};

        new AlertDialog.Builder(context)
                .setTitle("Em qual unidade o arquivo STL foi criado?")
                .setSingleChoiceItems(units, 0, null)
                .setPositiveButton("Confirmar", (dialog, which) -> {
                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    Unit unit;
                    switch (selectedPosition) {
                        case 1: unit = Unit.CENTIMETER; break;
                        case 2: unit = Unit.METER; break;
                        default: unit = Unit.MILLIMETER; break;
                    }
                    listener.onUnitSelected(unit, selectedPosition);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}