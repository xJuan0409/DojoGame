package com.dojogame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.ByteArrayInputStream;

/** Pequenos passos sintetizados em memória, sem depender de arquivo externo. */
final class SomPassos {

    private static final float TAXA_AMOSTRAGEM = 16_000;
    private final Clip[] variacoes = new Clip[2];
    private final Clip impactoShuriken;
    private final Clip impactoFumaca;
    private int proximaVariacao;
    private double volumeGeral = 1.0;

    SomPassos() {
        variacoes[0] = criarPasso(78, 0x4a31);
        variacoes[1] = criarPasso(92, 0x7c15);
        impactoShuriken = criarPasso(680, 0x2917);
        impactoFumaca = criarPasso(52, 0x6d43);
    }

    void tocar(boolean furtivo) {
        Clip passo = variacoes[proximaVariacao];
        proximaVariacao = (proximaVariacao + 1) % variacoes.length;

        if (passo == null) {
            return;
        }

        reproduzir(passo, furtivo ? -23.0f : -11.0f);
    }

    void definirVolume(double volume) {
        volumeGeral = Math.max(0, Math.min(1, volume));
    }

    void tocarImpactoShuriken() {
        reproduzir(impactoShuriken, -8.0f);
    }

    void tocarImpactoFumaca() {
        reproduzir(impactoFumaca, -5.0f);
    }

    void fechar() {
        for (Clip passo : variacoes) {
            if (passo != null) {
                passo.stop();
                passo.close();
            }
        }
        fecharClip(impactoShuriken);
        fecharClip(impactoFumaca);
    }

    private void reproduzir(Clip som, float volumeDesejado) {
        if (som == null || volumeGeral <= 0.001) {
            return;
        }
        if (som.isRunning()) {
            som.stop();
        }
        if (som.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volume = (FloatControl) som.getControl(
                    FloatControl.Type.MASTER_GAIN
            );
            float ajusteGeral = (float) (
                    20.0 * Math.log10(volumeGeral)
            );
            volume.setValue(Math.max(
                    volume.getMinimum(),
                    volumeDesejado + ajusteGeral
            ));
        }
        som.setFramePosition(0);
        som.start();
    }

    private void fecharClip(Clip som) {
        if (som != null) {
            som.stop();
            som.close();
        }
    }

    private Clip criarPasso(double frequencia, int sementeInicial) {
        try {
            int quantidadeAmostras = (int) (TAXA_AMOSTRAGEM * 0.075);
            byte[] dados = new byte[quantidadeAmostras * 2];
            int semente = sementeInicial;

            for (int indice = 0; indice < quantidadeAmostras; indice++) {
                double tempo = indice / TAXA_AMOSTRAGEM;
                double progresso = indice / (double) quantidadeAmostras;
                double envelope = Math.sin(Math.PI * progresso)
                        * Math.pow(1.0 - progresso, 1.7);

                semente = semente * 1103515245 + 12345;
                double ruido = ((semente >>> 16) & 0x7fff) / 16384.0 - 1.0;
                double impacto = Math.sin(2 * Math.PI * frequencia * tempo);
                double amostra = (impacto * 0.72 + ruido * 0.28) * envelope;
                short valor = (short) (amostra * 12_000);

                dados[indice * 2] = (byte) (valor & 0xff);
                dados[indice * 2 + 1] = (byte) ((valor >>> 8) & 0xff);
            }

            AudioFormat formato = new AudioFormat(
                    TAXA_AMOSTRAGEM, 16, 1, true, false
            );
            AudioInputStream fluxo = new AudioInputStream(
                    new ByteArrayInputStream(dados),
                    formato,
                    quantidadeAmostras
            );
            Clip clip = AudioSystem.getClip();
            clip.open(fluxo);

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl volume = (FloatControl) clip.getControl(
                        FloatControl.Type.MASTER_GAIN
                );
                volume.setValue(Math.max(volume.getMinimum(), -11.0f));
            }
            return clip;
        } catch (Exception excecao) {
            // O jogo continua normalmente em computadores sem saída de áudio.
            return null;
        }
    }
}
