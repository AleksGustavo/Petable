<?php
// ... (seu bloco PHP inicial vazio)
?>

<style>
    /* ... (seus estilos existentes) ... */
    
    /* Estilo para garantir que a lista do Autocomplete fique acima de outros elementos */
    .ui-autocomplete {
        z-index: 1050; /* Z-index maior que modais, se necessário */
        max-height: 200px;
        overflow-y: auto;
        overflow-x: hidden;
    }
</style>

<div class="d-flex justify-content-between align-items-center mb-2">
    <h2><i class="fas fa-cash-register me-2"></i> Ponto de Venda (PDV)</h2>
    
    <div>
        <button type="button" class="btn btn-info btn-sm" id="btn-fechar-caixa">
            <i class="fas fa-lock me-1"></i> Fechar Caixa
        </button>
    </div>
</div>

<div id="status-message-area">
    </div>

<form id="form-pdv" method="POST" action="vendas_processar.php">
    <div class="row g-2">
        
        <div class="col-lg-8">
            <div class="card shadow-sm mb-2">
                <div class="card-header bg-primary text-white p-2">
                    <h6 class="mb-0"><i class="fas fa-search me-2"></i> Adicionar Item</h6>
                    </div>
                <div class="card-body p-2">
                    <div class="row g-2">
                        <div class="col-md-7">
                            <label for="busca_item" class="form-label mb-1" style="font-size: 0.8rem;">Produto/Serviço (Cód. Barras / Nome)</label>
                            <input type="text" id="busca_item" name="busca_item" class="form-control form-control-sm" placeholder="Buscar por nome ou código...">
                            <input type="hidden" id="item_selecionado_id" name="item_selecionado_id">
                        </div>
                        <div class="col-md-2">
                            <label for="quantidade_item" class="form-label mb-1" style="font-size: 0.8rem;">Qtde.</label>
                            <input type="number" id="quantidade_item" name="quantidade_item" class="form-control form-control-sm" value="1" min="1">
                        </div>
                        <div class="col-md-3 d-flex align-items-end">
                            <button type="button" class="btn btn-success btn-sm w-100" id="btn-adicionar-item" disabled>
                                <i class="fas fa-cart-plus me-1"></i> Adicionar
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card shadow-sm">
                <div class="card-header bg-secondary text-white p-2">
                    <h6 class="mb-0"><i class="fas fa-list me-2"></i> Itens da Venda (<span id="contador_itens">0</span>)</h6>
                </div>
                <div class="card-body p-0">
                    <div class="table-responsive" id="area-itens-scroll">
                        <table class="table table-striped table-hover mb-0">
                            <thead>
                                <tr>
                                    <th>#</th>
                                    <th>Item</th>
                                    <th>Qtde.</th>
                                    <th>Preço Un.</th>
                                    <th>Subtotal</th>
                                    <th>Ação</th>
                                </tr>
                            </thead>
                            <tbody id="tabela_itens_venda">
                                <tr id="linha-vazia">
                                    <td colspan="6" class="text-center text-muted">Nenhum produto ou serviço adicionado.</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-lg-4">
            <div class="card p-2 shadow-sm">
                <h6 class="mb-2 text-primary"><i class="fas fa-user-tag me-2"></i> Cliente</h6>
                
                <div class="mb-2">
                    <label for="cliente_id" class="form-label mb-1" style="font-size: 0.8rem;">Cliente (Tabela `cliente`)</label>
                    <select id="cliente_id" name="cliente_id" class="form-select form-select-sm">
                        <option value="">(Venda Anônima)</option>
                        <option value="1">1 - Aleksander Gustavo</option>
                        </select>
                </div>
                
                <hr class="my-2">

                <h6 class="mb-2 text-primary"><i class="fas fa-file-invoice-dollar me-2"></i> Pagamento</h6>
                
                <div class="mb-2">
                    <label for="desconto" class="form-label mb-1" style="font-size: 0.8rem;">Desconto (R$)</label>
                    <input type="number" id="desconto" name="desconto" class="form-control form-control-sm" value="0.00" step="0.01" min="0">
                </div>

                <div class="mb-2">
                    <label for="forma_pagamento" class="form-label mb-1" style="font-size: 0.8rem;">Forma de Pagamento</label>
                    <select id="forma_pagamento" name="forma_pagamento" class="form-select form-select-sm" required>
                        <option value="">Selecione...</option>
                        <option value="pix">PIX</option>
                        <option value="cartao_credito">Cartão de Crédito</option>
                        <option value="cartao_debito">Cartão de Débito</option>
                        <option value="dinheiro">Dinheiro</option>
                        <option value="transferencia">Transferência</option>
                    </select>
                </div>

                <div class="mb-2">
                    <label for="observacoes" class="form-label mb-1" style="font-size: 0.8rem;">Obs.</label>
                    <textarea id="observacoes" name="observacoes" class="form-control form-control-sm" rows="1"></textarea>
                    </div>
                
                <hr class="my-2">

                <div class="d-flex justify-content-between align-items-center bg-dark text-white p-2 rounded mb-3">
                    <h5 class="mb-0">TOTAL:</h5>
                    <h5 class="mb-0" id="valor_total_display">R$ 0,00</h5>
                    <input type="hidden" id="valor_total" name="valor_total" value="0.00">
                    <input type="hidden" id="itens_venda_json" name="itens_venda_json">
                </div>

                <button type="submit" class="btn btn-danger w-100" id="btn-finalizar-venda" disabled>
                    <i class="fas fa-check-circle me-2"></i> FINALIZAR VENDA
                </button>
                
                <small class="text-muted mt-1 text-center" style="font-size: 0.7rem;">O Funcionário logado será registrado automaticamente.</small>
            </div>
        </div>
    </div>
</form>

<script src="https://code.jquery.com/jquery-3.7.1.min.js"></script> 
<script>
    // Usa jQuery(document).ready para garantir que o jQuery UI funcione
    jQuery(document).ready(function($) {
        
        const tabelaItens = document.getElementById('tabela_itens_venda');
        const valorTotalInput = document.getElementById('valor_total');
        const valorTotalDisplay = document.getElementById('valor_total_display');
        const descontoInput = document.getElementById('desconto');
        const btnFinalizar = document.getElementById('btn-finalizar-venda');
        
        // Elementos jQuery para Autocomplete
        const buscaItemInput = $('#busca_item'); 
        const itemSelecionadoId = document.getElementById('item_selecionado_id');
        const btnAdicionar = document.getElementById('btn-adicionar-item');
        const quantidadeItemInput = document.getElementById('quantidade_item');
        const itensVendaJson = document.getElementById('itens_venda_json');

        // Variável local para armazenar os dados do item da venda
        let itensVenda = []; 

        function calcularTotal() {
            let subtotalGeral = 0;
            itensVenda.forEach(item => {
                subtotalGeral += item.quantidade * item.preco;
            });

            let desconto = parseFloat(descontoInput.value) || 0;
            let totalFinal = Math.max(0, subtotalGeral - desconto);

            valorTotalInput.value = totalFinal.toFixed(2);
            valorTotalDisplay.textContent = 'R$ ' + totalFinal.toFixed(2).replace('.', ',');
            
            btnFinalizar.disabled = itensVenda.length === 0 || totalFinal <= 0;
            document.getElementById('contador_itens').textContent = itensVenda.length;
            
            // ATUALIZA O JSON PARA ENVIO NO FORMULÁRIO (CRÍTICO para o backend)
            itensVendaJson.value = JSON.stringify(itensVenda);
        }

        // FUNÇÃO PRINCIPAL: ADICIONA ITEM SELECIONADO À LISTA
        function adicionarItem(item) {
            const quantidade = parseInt(quantidadeItemInput.value);

            if (!item || quantidade <= 0) {
                return;
            }
            
            // Verifica se o item (produto/serviço) já está na lista
            const itemExistente = itensVenda.find(i => 
                i.id === item.id && i.tipo === item.tipo
            );

            if (itemExistente) {
                itemExistente.quantidade += quantidade;
            } else {
                const novoItem = {
                    id: item.id,       // ID do produto ou serviço (do banco)
                    nome: item.nome,   // Nome completo
                    preco: item.preco, // Preço unitário (do banco)
                    quantidade: quantidade,
                    tipo: item.tipo,   // 'produto' ou 'servico'
                    codigo_barras: item.codigo_barras || null // Adicionado para manter
                };
                itensVenda.push(novoItem);
            }
            
            renderizarItens();
            
            // Limpa os campos de busca após adicionar
            buscaItemInput.val('');
            itemSelecionadoId.value = '';
            quantidadeItemInput.value = 1;
            btnAdicionar.disabled = true;
            buscaItemInput.removeData('selectedItem'); // Limpa o dado armazenado

            // Foca na busca para o próximo item
            buscaItemInput.focus();
        }


        // ADICIONA EVENTO AO BOTÃO "ADICIONAR"
        btnAdicionar.addEventListener('click', function() {
            const selectedItem = buscaItemInput.data('selectedItem');
            if (selectedItem && itemSelecionadoId.value) {
                adicionarItem(selectedItem);
            } else {
                alert("Por favor, selecione um item da lista de sugestões.");
            }
        });

        // Simulação da função de remover item
        window.removerItem = function(id, tipo) {
            // Remove o item baseado na combinação ID e TIPO
            itensVenda = itensVenda.filter(item => !(item.id === id && item.tipo === tipo));
            renderizarItens();
        };

        function renderizarItens() {
            tabelaItens.innerHTML = '';
            if (itensVenda.length === 0) {
                tabelaItens.innerHTML = '<tr id="linha-vazia"><td colspan="6" class="text-center text-muted">Nenhum produto ou serviço adicionado.</td></tr>';
            } else {
                itensVenda.forEach((item, index) => {
                    const subtotal = item.quantidade * item.preco;
                    const tipoLabel = item.tipo === 'servico' ? '<i class="fas fa-tools text-info me-1"></i>' : '';
                    
                    tabelaItens.innerHTML += `
                        <tr>
                            <td>${index + 1}</td>
                            <td>${tipoLabel} ${item.nome}</td>
                            <td>
                                <input type="number" 
                                       data-item-id="${item.id}" 
                                       data-item-tipo="${item.tipo}" 
                                       value="${item.quantidade}" 
                                       min="1" 
                                       class="form-control form-control-sm text-center input-quantidade-pdv" 
                                       style="width: 60px;">
                            </td>
                            <td>R$ ${item.preco.toFixed(2).replace('.', ',')}</td>
                            <td>R$ ${subtotal.toFixed(2).replace('.', ',')}</td>
                            <td>
                                <button type="button" class="btn btn-sm btn-danger p-1" onclick="removerItem(${item.id}, '${item.tipo}')">
                                    <i class="fas fa-trash-alt"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }
            calcularTotal();
        }
        
        // EVENTO PARA ALTERAR QUANTIDADE DIRETO NA TABELA
        $(document).on('change', '.input-quantidade-pdv', function() {
            // Converte para Int para comparação
            const itemId = parseInt($(this).data('item-id'));
            const itemTipo = $(this).data('item-tipo');
            const novaQuantidade = parseInt($(this).val());
            
            if (novaQuantidade > 0) {
                const item = itensVenda.find(i => i.id === itemId && i.tipo === itemTipo);
                if (item) {
                    item.quantidade = novaQuantidade;
                    renderizarItens();
                }
            } else {
                // Se a quantidade for 0 ou menos, remove o item
                window.removerItem(itemId, itemTipo);
            }
        });

        descontoInput.addEventListener('input', calcularTotal);
        calcularTotal();
        
        /* --------------------------------------------------
         * LÓGICA DO AUTOCOMPLETE AJAX
         * -------------------------------------------------- */
        
        buscaItemInput.autocomplete({
            minLength: 2, // Começa a buscar a partir de 2 caracteres
            source: "pdv_buscar_itens.php", // Arquivo PHP que retorna o JSON
            
            // Função executada quando um item é SELECIONADO
            select: function(event, ui) {
                const item = ui.item;
                
                // 1. Preenche o campo de busca com o nome do item
                buscaItemInput.val(item.nome);
                
                // 2. Armazena o ID (e o TIPO) para a validação (Ex: "101-produto")
                itemSelecionadoId.value = `${item.id}-${item.tipo}`;
                
                // 3. Armazena o objeto completo para uso no 'Adicionar'
                buscaItemInput.data('selectedItem', item); 

                // 4. Habilita o botão de adicionar
                btnAdicionar.disabled = false;
                
                // 5. Move o foco para a quantidade
                quantidadeItemInput.focus(); 

                return false; // Previne que o valor padrão seja inserido no campo
            },
            // Função executada quando o foco muda para um item (útil para navegação com teclado)
            focus: function(event, ui) {
                buscaItemInput.val(ui.item.nome); // Exibe apenas o nome no campo
                return false;
            }
        }).autocomplete("instance")._renderItem = function(ul, item) {
            // Personaliza a exibição da lista de sugestões (usa o 'label' do JSON)
            // item.label contém o nome + (R$ preço)
            return $("<li>")
                .append(`<div>${item.label}</div>`)
                .appendTo(ul);
        };
        
        // Se o texto for alterado *após* uma seleção (digitando), desabilita o botão Adicionar
        buscaItemInput.on('input', function() {
            // Limpa o ID e o selectedItem se o texto não corresponder ao nome do item selecionado
            if ($(this).val() !== buscaItemInput.data('selectedItem')?.nome) {
                itemSelecionadoId.value = '';
                btnAdicionar.disabled = true;
                buscaItemInput.removeData('selectedItem'); 
            }
        });
        
        // Ativar a busca ao pressionar ENTER no campo de busca (se houver item selecionado)
        buscaItemInput.keypress(function(e) {
            if (e.which === 13) { // 13 é a tecla ENTER
                e.preventDefault();
                if (!btnAdicionar.disabled) {
                    btnAdicionar.click();
                }
            }
        });

    });
</script>