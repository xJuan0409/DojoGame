package com.dojogame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Calcula o menor caminho livre em uma grade usando o algoritmo A*. */
final class EncontradorCaminhoAStar {

    private static final int[][] DIRECOES = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };
    private static final int DIRECAO_INICIAL = DIRECOES.length;
    private static final int CUSTO_PASSO = 100;
    private static final int CUSTO_CURVA = 1;

    private EncontradorCaminhoAStar() {
    }

    static List<Celula> encontrar(
            boolean[][] bloqueios,
            Celula origem,
            Celula destino
    ) {
        if (!gradeValida(bloqueios)
                || !estaDentro(bloqueios, origem)
                || !estaDentro(bloqueios, destino)
                || estaBloqueada(bloqueios, origem)
                || estaBloqueada(bloqueios, destino)) {
            return List.of();
        }

        int linhas = bloqueios.length;
        int colunas = bloqueios[0].length;
        int[][][] melhorCusto = new int[linhas][colunas][DIRECOES.length + 1];
        Estado[][][] anterior =
                new Estado[linhas][colunas][DIRECOES.length + 1];
        boolean[][][] fechado =
                new boolean[linhas][colunas][DIRECOES.length + 1];

        for (int[][] custosDaLinha : melhorCusto) {
            for (int[] custosDaCelula : custosDaLinha) {
                Arrays.fill(custosDaCelula, Integer.MAX_VALUE);
            }
        }

        PriorityQueue<No> abertos = new PriorityQueue<>(
                Comparator.comparingInt(No::prioridade)
                        .thenComparingInt(No::heuristica)
                        .thenComparingLong(No::ordem)
        );

        Estado estadoInicial = new Estado(origem, DIRECAO_INICIAL);
        long proximaOrdem = 0;
        melhorCusto[origem.linha()][origem.coluna()][DIRECAO_INICIAL] = 0;
        abertos.add(new No(
                estadoInicial,
                0,
                distanciaManhattan(origem, destino) * CUSTO_PASSO,
                proximaOrdem++
        ));

        while (!abertos.isEmpty()) {
            No atual = abertos.poll();
            Estado estadoAtual = atual.estado();
            Celula celulaAtual = estadoAtual.celula();
            int direcaoAtual = estadoAtual.direcao();

            if (fechado[celulaAtual.linha()][celulaAtual.coluna()][direcaoAtual]
                    || atual.custoPercorrido()
                    != melhorCusto[celulaAtual.linha()][celulaAtual.coluna()][direcaoAtual]) {
                continue;
            }

            if (celulaAtual.equals(destino)) {
                return reconstruirCaminho(anterior, estadoAtual);
            }

            fechado[celulaAtual.linha()][celulaAtual.coluna()][direcaoAtual] = true;

            for (int indiceDirecao = 0;
                 indiceDirecao < DIRECOES.length;
                 indiceDirecao++) {
                int[] direcao = DIRECOES[indiceDirecao];
                Celula vizinha = new Celula(
                        celulaAtual.linha() + direcao[0],
                        celulaAtual.coluna() + direcao[1]
                );

                if (!estaDentro(bloqueios, vizinha)
                        || estaBloqueada(bloqueios, vizinha)
                        || fechado[vizinha.linha()][vizinha.coluna()][indiceDirecao]) {
                    continue;
                }

                boolean mudouDirecao = direcaoAtual != DIRECAO_INICIAL
                        && direcaoAtual != indiceDirecao;
                int novoCusto = atual.custoPercorrido()
                        + CUSTO_PASSO
                        + (mudouDirecao ? CUSTO_CURVA : 0);

                if (novoCusto >= melhorCusto
                        [vizinha.linha()][vizinha.coluna()][indiceDirecao]) {
                    continue;
                }

                melhorCusto[vizinha.linha()][vizinha.coluna()][indiceDirecao] =
                        novoCusto;
                Estado estadoVizinho = new Estado(vizinha, indiceDirecao);
                anterior[vizinha.linha()][vizinha.coluna()][indiceDirecao] =
                        estadoAtual;
                abertos.add(new No(
                        estadoVizinho,
                        novoCusto,
                        distanciaManhattan(vizinha, destino) * CUSTO_PASSO,
                        proximaOrdem++
                ));
            }
        }

        return List.of();
    }

    private static List<Celula> reconstruirCaminho(
            Estado[][][] anterior,
            Estado destino
    ) {
        List<Celula> caminhoInvertido = new ArrayList<>();
        Estado atual = destino;

        while (atual != null) {
            caminhoInvertido.add(atual.celula());
            atual = anterior
                    [atual.celula().linha()]
                    [atual.celula().coluna()]
                    [atual.direcao()];
        }

        Collections.reverse(caminhoInvertido);
        return caminhoInvertido;
    }

    private static int distanciaManhattan(Celula origem, Celula destino) {
        return Math.abs(origem.linha() - destino.linha())
                + Math.abs(origem.coluna() - destino.coluna());
    }

    private static boolean gradeValida(boolean[][] bloqueios) {
        return bloqueios != null
                && bloqueios.length > 0
                && bloqueios[0] != null
                && bloqueios[0].length > 0;
    }

    private static boolean estaDentro(boolean[][] bloqueios, Celula celula) {
        return celula.linha() >= 0
                && celula.coluna() >= 0
                && celula.linha() < bloqueios.length
                && celula.coluna() < bloqueios[0].length;
    }

    private static boolean estaBloqueada(
            boolean[][] bloqueios,
            Celula celula
    ) {
        return bloqueios[celula.linha()][celula.coluna()];
    }

    record Celula(int linha, int coluna) {
    }

    private record Estado(Celula celula, int direcao) {
    }

    private record No(
            Estado estado,
            int custoPercorrido,
            int heuristica,
            long ordem
    ) {
        private int prioridade() {
            return custoPercorrido + heuristica;
        }
    }
}
