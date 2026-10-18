package com.example.trabalhocalculo3.util;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trabalhocalculo3.R;

public class InicioActivity extends AppCompatActivity {

    private Button button;

    private Button btnRealidadeAumentada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inicio);

        button = findViewById(R.id.btnComecar);
        btnRealidadeAumentada = findViewById(R.id.btnRealidadeAumentada);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(InicioActivity.this, MainActivity.class);
                startActivity(intent);

            }
        });

        btnRealidadeAumentada.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(InicioActivity.this, RealidadeAumentada.class);
                startActivity(intent);
            }
        });

    }
}