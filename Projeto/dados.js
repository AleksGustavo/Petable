

const conteudoPrincipal = document.getElementById('conteudo-principal');

function carregarConteudo(pagina) {
    if (!conteudoPrincipal) return;

    conteudoPrincipal.innerHTML = '<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Carregando...</p></div>';

    fetch(pagina)
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => { throw new Error('Erro ao carregar a página. Status: ' + response.status + (text ? ' | Resposta: ' + text.substring(0, 100) : '')); });
            }
            return response.text();
        })
        .then(html => {
            conteudoPrincipal.innerHTML = html;
            
            configurarManipuladorFormulario();
        })
        .catch(error => {
            conteudoPrincipal.innerHTML = `<div class="alert alert-danger">Erro ao carregar conteúdo: ${error.message}</div>`;
            console.error('Erro de AJAX:', error);
        });
}

function configurarManipuladorFormulario() {
    
    const formIds = ['form-cadastro-cliente', 'form-cadastro-pet']; 

    formIds.forEach(id => {
        const form = document.getElementById(id);
        if (form) {
            form.removeEventListener('submit', handleFormSubmit);
            form.addEventListener('submit', handleFormSubmit);
        }
    });
}

function handleFormSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const actionUrl = form.getAttribute('action');
    
    
    const formData = new FormData(form); 
    
    
    const statusArea = document.getElementById('status-message-area') || form.querySelector('#status-message-area') || form.closest('.card-body').querySelector('#status-message-area');
    
    if (statusArea) {
        statusArea.innerHTML = '<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-primary"></i> Processando...</div>';
    }

    fetch(actionUrl, {
        method: 'POST',
        body: formData 
    })
    .then(response => {
        if (!response.ok) {
            
             return response.text().then(text => { throw new Error('Erro do Servidor. Status: ' + response.status + ' | Resposta: ' + text.substring(0, 100)); });
        }
        return response.json(); 
    })
    .then(data => {
        
        if (statusArea) {
            if (data.success) {
                statusArea.innerHTML = `<div class="alert alert-success">${data.message}</div>`;
                
                
                if (form.id === 'form-cadastro-cliente') {
                     
                     form.reset(); 
                } else if (form.id === 'form-cadastro-pet') {
                    
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
        
        if (statusArea) {
             statusArea.innerHTML = `<div class="alert alert-danger">Erro inesperado no AJAX: ${error.message}</div>`;
        }
        console.error('Erro no processamento do formulário:', error);
    });
}


// --- LÓGICA DE BUSCA RÁPIDA (DEVE SER MANTIDA SE USAR APENAS JAVASCRIPT VANILLA) ---

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
            
            
            console.log('Excluindo cliente ID:', clienteId);
        }
    }
});

// Inicializa os manipuladores de formulário no carregamento inicial do dashboard
document.addEventListener('DOMContentLoaded', configurarManipuladorFormulario);