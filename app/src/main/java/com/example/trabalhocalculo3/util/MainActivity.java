package com.example.trabalhocalculo3.util;

import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertDensity;
import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertVolume;

import android.os.Bundle;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.net.Uri;

import com.example.trabalhocalculo3.R;
import com.example.trabalhocalculo3.mathMotor.MassCalculator;
import com.example.trabalhocalculo3.mathMotor.VolumeCalculator;
import com.example.trabalhocalculo3.mesh.Mesh3D;
import com.example.trabalhocalculo3.mesh.MeshImporter;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.trabalhocalculo3.graphics.GraphSurfaceView;


public class MainActivity extends AppCompatActivity {
    private TextView txtDensityLabel;
    private TextView txtResults;
    private double density = 1.0;
    private double rawVolume = 1.0;
    private double mass = 0.0;
    private Button btnImportStl;
    private TextView txtImportStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Mesh3D currentMesh;
    private final ActivityResultLauncher<String[]> openStlLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onStlSelected);
    private Spinner spinner;
    private Unit modelUnit = Unit.MILLIMETER; // unidade utilizada pelo modelo; informado pelo usuário
    private Unit displayUnit = Unit.MILLIMETER; // unidade mostrada; pode ser alterada pelo usuário
    private GraphSurfaceView graphView;
    private double[] rawCenterOfMass = new double[]{0, 0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    graphView = findViewById(R.id.graphView);

    btnImportStl = findViewById(R.id.btnImport);
    txtImportStatus = findViewById(R.id.textImportStatus);
    SeekBar seekDensity = findViewById(R.id.seekDensity);
    txtDensityLabel = findViewById(R.id.txtDensityLabel);
    txtResults = findViewById(R.id.txtResults);
    spinner = findViewById(R.id.spinner);

        btnImportStl.setOnClickListener(v -> openStlLauncher.launch(new String[]{"*/*"}));

        seekDensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                density = progress / 100.0;
                updateResults();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch(position) {
                    case 0:
                        displayUnit = Unit.MILLIMETER;
                        break;
                    case 1:
                        displayUnit = Unit.CENTIMETER;
                        break;
                    case 2:
                        displayUnit = Unit.METER;
                        break;
                }
                updateResults();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    private void sendMeshToRenderer(Mesh3D mesh) {
        if (graphView == null || mesh == null) {
            return;
        }

        graphView.getRenderer().setMesh(mesh);
        graphView.requestRender();
    }

    private void updateResults() {
        double displayedVolume = convertVolume(
                rawVolume,
                modelUnit,
                displayUnit
        );

        double displayedDensity = convertDensity(
                density,
                modelUnit,
                displayUnit
        );

        mass = rawVolume * density;

        // Conversão das coordenadas do Centro de Massa para a unidade selecionada
        double factor = modelUnit.getMetersPerUnit() / displayUnit.getMetersPerUnit();
        double cmX = rawCenterOfMass[0] * factor;
        double cmY = rawCenterOfMass[1] * factor;
        double cmZ = rawCenterOfMass[2] * factor;

        txtDensityLabel.setText(String.format("Densidade (kg/%s): %.6f",
                displayUnit.getVolumeSymbol(), displayedDensity));

        txtResults.setText(String.format("Volume: %.9f %s\nMassa: %.2f kg\nCentro de massa: (%.2f, %.2f, %.2f) %s",
                displayedVolume, displayUnit.getVolumeSymbol(), mass, cmX, cmY, cmZ, displayUnit.getSymbol()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void onStlSelected(Uri uri) {
        if (uri == null) {
            txtImportStatus.setText("Seleção cancelada.");
            return;
        }

        askModelUnit(uri);
    }

    private void askModelUnit(Uri uri) {
        String[] units = {
                "Milímetros (mm)",
                "Centímetros (cm)",
                "Metros (m)"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Em qual unidade o arquivo STL foi criado?")
                .setSingleChoiceItems(units, 0, null)
                .setPositiveButton("Confirmar", (dialog, which) -> {

                    int selectedPosition = ((androidx.appcompat.app.AlertDialog) dialog).getListView().getCheckedItemPosition();

                    switch (selectedPosition) {
                        case 0:
                            modelUnit = Unit.MILLIMETER;
                            break;
                        case 1:
                            modelUnit = Unit.CENTIMETER;
                            break;
                        case 2:
                            modelUnit = Unit.METER;
                            break;
                    }

                    displayUnit = modelUnit;
                    spinner.setSelection(selectedPosition);

                    importStl(uri);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void importStl(Uri uri) {
        txtImportStatus.setText("Importando STL...");
        btnImportStl.setEnabled(false);

        //Parsing fora da main thread.
        executor.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new Exception("Não foi possível abrir o arquivo.");

                }

                Mesh3D mesh = MeshImporter.importStl(inputStream);
                if(mesh.getPositions() == null || mesh.getNormals() == null){
                    throw new Exception("erro na importação.");
                }
                currentMesh = mesh;
                runOnUiThread(() -> {
                    txtImportStatus.setText("Importação concluída com sucesso");
                    btnImportStl.setEnabled(true);
                    rawVolume = VolumeCalculator.computeVolume(mesh);
                    rawVolume = VolumeCalculator.computeVolume(mesh);
                    rawCenterOfMass = MassCalculator.computeCenterOfMass(mesh);
                    updateResults();

                    
                     sendMeshToRenderer(currentMesh);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    txtImportStatus.setText(e.getMessage());
                    btnImportStl.setEnabled(true);
                });
            }
        });
    }
}
