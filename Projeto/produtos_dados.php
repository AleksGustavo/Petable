<?php
// Arquivo: produtos_get_dados_aux.php
// Funções para carregar dados auxiliares (Categorias, Fornecedores) para formulários.

// Garante que a conexão com o BD está incluída
require_once 'conexao.php'; 

// Variável $pdo deve estar disponível após o require_once

function get_categorias($pdo) {
    try {
        $stmt = $pdo->query("SELECT id, nome FROM categoria_produto ORDER BY nome");
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        // Em caso de erro (ex: tabela categorias_produtos não existe)
        error_log("Erro ao buscar categorias: " . $e->getMessage());
        return [];
    }
}

function get_fornecedores($pdo) {
    try {
        // Usamos nome_fantasia (ou razao_social) conforme sua estrutura
        $stmt = $pdo->query("SELECT id, nome_fantasia FROM fornecedor WHERE ativo = 1 ORDER BY nome_fantasia");
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        error_log("Erro ao buscar fornecedores: " . $e->getMessage());
        return [];
    }
}
// Não feche a tag PHP aqui para evitar problemas de header