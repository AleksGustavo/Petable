// Arquivo: dados.js (Versão Corrigida para suportar File Upload com FormData)

// ID da área de conteúdo principal
const conteudoPrincipal = document.getElementById('conteudo-principal');

// Função para carregar o conteúdo de uma página via Fetch (AJAX)
function carregarConteudo(pagina) {
    if (!conteudoPrincipal) return;

    // Adiciona um placeholder de carregamento
    conteudoPrincipal.innerHTML = '<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Carregando...</p></div>';

    fetch(pagina)
        .then(response => {
            if (!response.ok) {
                // Se o status for de erro, tenta ler o erro do servidor
                return response.text().then(text => { throw new Error('Erro ao carregar a página. Status: ' + response.status + (text ? ' | Resposta: ' + text.substring(0, 100) : '')); });
            }
            return response.text();
        })
        .then(html => {
            // Injeta o HTML carregado
            conteudoPrincipal.innerHTML = html;
            
            // Reconfigura os manipuladores de formulário e eventos
            configurarManipuladorFormulario();
        })
        .catch(error => {
            conteudoPrincipal.innerHTML = `<div class="alert alert-danger">Erro ao carregar conteúdo: ${error.message}</div>`;
            console.error('Erro de AJAX:', error);
        });
}

// Função que configura a interceptação do formulário para envio AJAX
function configurarManipuladorFormulario() {
    
    // Lista de IDs de formulários para manipulação AJAX.
    // Incluímos 'form-cadastro-pet' aqui.
    const formIds = ['form-cadastro-cliente', 'form-cadastro-pet']; 

    formIds.forEach(id => {
        const form = document.getElementById(id);
        if (form) {
            // Remove listeners antigos e adiciona novos (para garantir que não haja duplicação)
            form.removeEventListener('submit', handleFormSubmit);
            form.addEventListener('submit', handleFormSubmit);
        }
    });
}

// Função genérica para interceptar o SUBMIT do formulário
function handleFormSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const actionUrl = form.getAttribute('action');
    
    // **ESSENCIAL PARA UPLOAD DE ARQUIVOS (FOTOS)**
    const formData = new FormData(form); 
    
    // Encontra a área de mensagem para exibir o status
    const statusArea = document.getElementById('status-message-area') || form.querySelector('#status-message-area') || form.closest('.card-body').querySelector('#status-message-area');
    
    if (statusArea) {
        statusArea.innerHTML = '<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-primary"></i> Processando...</div>';
    }

    fetch(actionUrl, {
        method: 'POST',
        body: formData // Envia o FormData diretamente. Isso é que permite o upload de arquivos.
    })
    .then(response => {
        if (!response.ok) {
            // Tenta obter o corpo da resposta para debug
             return response.text().then(text => { throw new Error('Erro do Servidor. Status: ' + response.status + ' | Resposta: ' + text.substring(0, 100)); });
        }
        return response.json(); // Espera uma resposta JSON
    })
    .then(data => {
        // Tratamento da resposta JSON do PHP
        if (statusArea) {
            if (data.success) {
                statusArea.innerHTML = `<div class="alert alert-success">${data.message}</div>`;
                
                // Redireciona ou atualiza o conteúdo após o cadastro
                if (form.id === 'form-cadastro-cliente') {
                     // Limpa o formulário de cliente após sucesso
                     form.reset(); 
                } else if (form.id === 'form-cadastro-pet') {
                    // Após cadastrar o pet, redireciona para a ficha do dono
                    const clienteId = form.querySelector('input[name="cliente_id"]').value;
                    setTimeout(() => {
                        carregarConteudo('clientes_detalhes.php?id=' + clienteId);
                    }, 1500); 
                }
                
            } else {
                statusArea.innerHTML = `<div class="alert alert-danger">${data.message}</div>`;
            }
        }
    })
    .catch(error => {
        // Captura erros de rede ou falha ao ler o JSON
        if (statusArea) {
             statusArea.innerHTML = `<div class="alert alert-danger">Erro inesperado no AJAX: ${error.message}</div>`;
        }
        console.error('Erro no processamento do formulário:', error);
    });
}


// --- LÓGICA DE BUSCA RÁPIDA (DEVE SER MANTIDA SE USAR APENAS JAVASCRIPT VANILLA) ---
// Se você está usando jQuery, esta parte deve estar no seu dashboard.php ou ser adaptada.
document.addEventListener('submit', function(event) {
    if (event.target.id === 'form-busca-cliente-rapida') {
        event.preventDefault();
        const form = event.target;
        const termo = form.querySelector('#termo_busca').value;
        const resultadoArea = document.getElementById('resultado-busca-rapida');
        
        resultadoArea.innerHTML = '<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-primary"></i> Buscando...</div>';

        fetch(`clientes_buscar_rapido.php?termo_busca=${encodeURIComponent(termo)}`)
            .then(response => response.text())
            .then(html => {
                resultadoArea.innerHTML = html;
            })
            .catch(error => {
                resultadoArea.innerHTML = `<div class="alert alert-danger mt-3">Erro ao buscar clientes: ${error.message}</div>`;
            });
    }
});
// ---------------------------------------------------------------------------------


// --- INICIALIZAÇÃO (Carregamento da Página) ---

// 1. Configura a navegação AJAX para os botões do menu
document.addEventListener('click', function(event) {
    const target = event.target.closest('.item-menu-ajax');
    if (target) {
        event.preventDefault();
        const pagina = target.getAttribute('data-pagina');
        if (pagina) {
            carregarConteudo(pagina);
        }
    }
    
    // 2. Configura a exclusão de clientes/pets (Exemplo de evento para exclusão)
    if (event.target.classList.contains('excluir-cliente')) {
        event.preventDefault();
        const clienteId = event.target.getAttribute('data-id');
        if (confirm('Tem certeza que deseja excluir o Cliente ID ' + clienteId + '? Esta ação é irreversível.')) {
            // Lógica para exclusão AJAX
            // Aqui você chamaria um arquivo PHP como 'clientes_processar.php?acao=excluir&id='
            console.log('Excluindo cliente ID:', clienteId);
        }
    }
});

// Inicializa os manipuladores de formulário no carregamento inicial do dashboard
document.addEventListener('DOMContentLoaded', configurarManipuladorFormulario);