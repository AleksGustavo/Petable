<?php

require_once 'conexao.php';

function get_categorias($pdo) {
    try {
        $stmt = $pdo->query("SELECT id, nome FROM categoria_produto ORDER BY nome");
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        error_log("Erro ao buscar categorias: " . $e->getMessage());
        return [];
    }
}

function get_fornecedores($pdo) {
    try {
        $stmt = $pdo->query("SELECT id, nome_fantasia FROM fornecedor WHERE ativo = 1 ORDER BY nome_fantasia");
        return $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        error_log("Erro ao buscar fornecedores: " . $e->getMessage());
        return [];
    }
}
