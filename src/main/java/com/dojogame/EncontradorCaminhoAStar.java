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
        int[][] melhorCusto = new int[linhas][colunas];
        Celula[][] anterior = new Celula[linhas][colunas];
        boolean[][] fechado = new boolean[linhas][colunas];

        for (int[] custosDaLinha : melhorCusto) {
            Arrays.fill(custosDaLinha, Integer.MAX_VALUE);
        }

        PriorityQueue<No> abertos = new PriorityQueue<>(
                Comparator.comparingInt(No::prioridade)
                        .thenComparingInt(No::heuristica)
        );

        melhorCusto[origem.linha()][origem.coluna()] = 0;
        abertos.add(new No(origem, 0, distanciaManhattan(origem, destino)));

        while (!abertos.isEmpty()) {
            No atual = abertos.poll();
            Celula celulaAtual = atual.celula();

            if (fechado[celulaAtual.linha()][celulaAtual.coluna()]
                    || atual.custoPercorrido()
                    != melhorCusto[celulaAtual.linha()][celulaAtual.coluna()]) {
                continue;
            }

            if (celulaAtual.equals(destino)) {
                return reconstruirCaminho(anterior, origem, destino);
            }

            fechado[celulaAtual.linha()][celulaAtual.coluna()] = true;

            for (int[] direcao : DIRECOES) {
                Celula vizinha = new Celula(
                        celulaAtual.linha() + direcao[0],
                        celulaAtual.coluna() + direcao[1]
                );

                if (!estaDentro(bloqueios, vizinha)
                        || estaBloqueada(bloqueios, vizinha)
                        || fechado[vizinha.linha()][vizinha.coluna()]) {
                    continue;
                }

                int novoCusto = atual.custoPercorrido() + 1;

                if (novoCusto >= melhorCusto[vizinha.linha()][vizinha.coluna()]) {
                    continue;
                }

                melhorCusto[vizinha.linha()][vizinha.coluna()] = novoCusto;
                anterior[vizinha.linha()][vizinha.coluna()] = celulaAtual;
                abertos.add(new No(
                        vizinha,
                        novoCusto,
                        distanciaManhattan(vizinha, destino)
                ));
            }
        }

        return List.of();
    }

    private static List<Celula> reconstruirCaminho(
            Celula[][] anterior,
            Celula origem,
            Celula destino
    ) {
        List<Celula> caminhoInvertido = new ArrayList<>();
        Celula atual = destino;

        while (atual != null) {
            caminhoInvertido.add(atual);

            if (atual.equals(origem)) {
                Collections.reverse(caminhoInvertido);
                return caminhoInvertido;
            }

            atual = anterior[atual.linha()][atual.coluna()];
        }

        return List.of();
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

    private record No(
            Celula celula,
            int custoPercorrido,
            int heuristica
    ) {
        private int prioridade() {
            return custoPercorrido + heuristica;
        }
    }
}
