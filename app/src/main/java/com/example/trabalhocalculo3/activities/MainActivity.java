package com.example.trabalhocalculo3.activities;

import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertDensity;
import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertVolume;
import static com.example.trabalhocalculo3.mathMotor.InertiaCalculator.computeInertiaTensor;

import android.os.Bundle;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.net.Uri;

import com.example.trabalhocalculo3.R;
import com.example.trabalhocalculo3.mathMotor.CalculationResult;
import com.example.trabalhocalculo3.mathMotor.MassCalculator;
import com.example.trabalhocalculo3.mathMotor.VolumeCalculator;
import com.example.trabalhocalculo3.mesh.Mesh3D;
import com.example.trabalhocalculo3.mesh.MeshImporter;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.trabalhocalculo3.graphics.GraphSurfaceView;
import com.example.trabalhocalculo3.util.ResultFormatter;
import com.example.trabalhocalculo3.util.Unit;
import com.example.trabalhocalculo3.util.UnitSelectionDialog;


public class MainActivity extends AppCompatActivity {
    private TextView txtDensityLabel;
    private TextView txtResults;
    private double density = 1.0;
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
    private CheckBox chkShowCenterOfMass;
    private CalculationResult currentResult = new CalculationResult();

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
        chkShowCenterOfMass = findViewById(R.id.checkBoxCenterOfMass);

        btnImportStl.setOnClickListener(v -> {
            openStlLauncher.launch(new String[]{"*/*"});
        });

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

        chkShowCenterOfMass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (graphView != null) {
                graphView.getRenderer().setShowCenterOfMass(isChecked);
                graphView.requestRender();
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
        // Se já houver uma malha carregada, recalcula a massa e o tensor com a densidade atual
        if (currentMesh != null) {
            double mass = currentResult.getRawVolume() * density;
            currentResult.setMass(mass);
            currentResult.setInertiaTensor(computeInertiaTensor(currentMesh, density, currentResult.getRawCenterOfMass()));
        }

        txtDensityLabel.setText(ResultFormatter.formatDensityLabel(density, modelUnit, displayUnit));
        txtResults.setText(ResultFormatter.formatResults(currentResult, modelUnit, displayUnit));
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
        chkShowCenterOfMass.setChecked(false);

        UnitSelectionDialog.show(this, (selectedUnit, position) -> {
            modelUnit = selectedUnit;
            displayUnit = selectedUnit;
            spinner.setSelection(position);
            importStl(uri);
        });
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

                    double rawVolume = VolumeCalculator.computeVolume(mesh);
                    double[] centerOfMass = MassCalculator.computeCenterOfMass(mesh);
                    double mass = rawVolume * density;

                    currentResult.setRawVolume(rawVolume);
                    currentResult.setRawCenterOfMass(centerOfMass);
                    currentResult.setMass(mass);
                    currentResult.setInertiaTensor(computeInertiaTensor(mesh, density, centerOfMass));

                    chkShowCenterOfMass.setVisibility(View.VISIBLE);

                    updateResults();

                    // Atualiza o renderizador com a malha e as coordenadas do Centro de Massa
                    graphView.getRenderer().setCenterOfMass(currentResult.getRawCenterOfMass());
                    graphView.getRenderer().setShowCenterOfMass(chkShowCenterOfMass.isChecked());
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
