document.addEventListener("DOMContentLoaded", () => {
    const greetingForm = document.getElementById("greetingForm");
    const squareForm = document.getElementById("squareForm");
    const timeBtn = document.getElementById("timeBtn");

    const resultBox = document.getElementById("result-box");
    const errorBox = document.getElementById("error-box");

    // Limpia o muestra los estados de salida
    function showLoading() {
        errorBox.style.display = "none";
        resultBox.innerText = "Cargando respuesta del servidor...";
    }

    function showSuccess(message) {
        errorBox.style.display = "none";
        resultBox.innerText = message;
    }

    function showError(message) {
        resultBox.innerText = "Operación fallida.";
        errorBox.innerText = message;
        errorBox.style.display = "block";
    }

    // Función genérica Fetch con manejo explícito de errores HTTP y de red
    async function makeAsyncRequest(url) {
        showLoading();
        try {
            const response = await fetch(url);

            if (!response.ok) {
                // Manejo de errores devueltos con estatus 4xx o 5xx por el servidor
                let errorMsg = `Error HTTP ${response.status} (${response.statusText})`;
                try {
                    const errorHtml = await response.text();
                    // Extraer mensaje limpio si viene HTML
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(errorHtml, 'text/html');
                    const p = doc.querySelector('p');
                    if (p) errorMsg += `: ${p.innerText}`;
                } catch (e) {}
                showError(errorMsg);
                return;
            }

            const data = await response.json();
            return data;

        } catch (error) {
            // Manejo de errores de red (e.g., servidor apagado)
            showError("Error de red o conexión rechazada por el servidor.");
        }
    }

    // 1. Evento Formulario Greeting
    greetingForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const name = document.getElementById("nameInput").value;
        const data = await makeAsyncRequest(`/greeting?name=${encodeURIComponent(name)}`);
        if (data) {
            showSuccess(`Respuesta Servidor: ${data.greeting}`);
        }
    });

    // 2. Evento Formulario Square
    squareForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const value = document.getElementById("numberInput").value;
        const data = await makeAsyncRequest(`/square?value=${encodeURIComponent(value)}`);
        if (data) {
            showSuccess(`Entrada: ${data.value} | Cuadrado: ${data.square}`);
        }
    });

    // 3. Evento Consulta Servertime
    timeBtn.addEventListener("click", async () => {
        const data = await makeAsyncRequest("/servertime");
        if (data) {
            showSuccess(`Hora Remota del Servidor: ${data.time}`);
        }
    });
});