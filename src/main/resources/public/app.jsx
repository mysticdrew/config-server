const { useEffect, useMemo, useState } = React;

function App() {
  const [configs, setConfigs] = useState([]);
  const [selected, setSelected] = useState("");
  const [doc, setDoc] = useState(null);
  const [error, setError] = useState("");
  const [createName, setCreateName] = useState("");
  const [fieldForm, setFieldForm] = useState({ key: "", type: "STRING", value: "" });
  const [editingKey, setEditingKey] = useState("");

  async function request(path, options = {}) {
    const res = await fetch(path, options);
    if (!res.ok) {
      let message = `Request failed (${res.status})`;
      try {
        const data = await res.json();
        if (data.error) {
          message = data.error;
        }
      } catch (_) {}
      throw new Error(message);
    }
    return res;
  }

  async function loadConfigs() {
    setError("");
    try {
      const res = await request("/api/v1/config");
      const data = await res.json();
      setConfigs(data);
      if (!selected && data.length > 0) {
        setSelected(data[0].name);
      }
      if (selected && !data.find((item) => item.name === selected)) {
        setSelected(data.length ? data[0].name : "");
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function loadDoc(name) {
    if (!name) {
      setDoc(null);
      return;
    }
    setError("");
    try {
      const res = await request(`/api/v1/config/${encodeURIComponent(name)}/json`);
      const data = await res.json();
      setDoc(data);
    } catch (err) {
      setDoc(null);
      setError(err.message);
    }
  }

  useEffect(() => {
    loadConfigs();
  }, []);

  useEffect(() => {
    loadDoc(selected);
    setEditingKey("");
    setFieldForm({ key: "", type: "STRING", value: "" });
  }, [selected]);

  async function createConfig(e) {
    e.preventDefault();
    setError("");
    try {
      await request("/api/v1/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: createName.trim() })
      });
      setCreateName("");
      await loadConfigs();
      setSelected(createName.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function removeConfig(name) {
    if (!window.confirm(`Delete config '${name}'?`)) {
      return;
    }
    setError("");
    try {
      await request(`/api/v1/config/${encodeURIComponent(name)}`, { method: "DELETE" });
      await loadConfigs();
      if (selected === name) {
        setSelected("");
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function submitField(e) {
    e.preventDefault();
    if (!selected) {
      return;
    }
    setError("");
    try {
      await request(
        `/api/v1/config/${encodeURIComponent(selected)}/fields/${encodeURIComponent(fieldForm.key.trim())}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ type: fieldForm.type, value: fieldForm.value })
        }
      );
      setEditingKey("");
      setFieldForm({ key: "", type: "STRING", value: "" });
      await loadDoc(selected);
      await loadConfigs();
    } catch (err) {
      setError(err.message);
    }
  }

  async function removeField(key) {
    if (!selected || !window.confirm(`Delete field '${key}'?`)) {
      return;
    }
    setError("");
    try {
      await request(
        `/api/v1/config/${encodeURIComponent(selected)}/fields/${encodeURIComponent(key)}`,
        { method: "DELETE" }
      );
      await loadDoc(selected);
      await loadConfigs();
    } catch (err) {
      setError(err.message);
    }
  }

  function startEdit(field) {
    setEditingKey(field.key);
    setFieldForm({ key: field.key, type: field.type, value: String(field.value) });
  }

  const propertiesUrl = useMemo(() => {
    if (!selected) {
      return "";
    }
    return `/api/v1/config/${encodeURIComponent(selected)}`;
  }, [selected]);

  return (
    <div className="app">
      <div className="header">
        <h1>Internal Config Supply</h1>
        <p>Create and maintain typed fields for Java properties configs.</p>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="layout">
        <section className="card">
          <h2>Config Files</h2>
          <form onSubmit={createConfig}>
            <input
              placeholder="new-config-name"
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              required
            />
            <button type="submit">Create config</button>
          </form>

          <div>
            {configs.map((config) => (
              <div
                className={`config-item ${selected === config.name ? "active" : ""}`}
                key={config.name}
                onClick={() => setSelected(config.name)}
              >
                <span>
                  {config.name} ({config.fieldCount})
                </span>
                <button
                  className="danger"
                  onClick={(e) => {
                    e.stopPropagation();
                    removeConfig(config.name);
                  }}
                >
                  Delete
                </button>
              </div>
            ))}
          </div>
        </section>

        <section className="card">
          <h2>Fields {selected ? `for ${selected}` : ""}</h2>
          {selected && (
            <p>
              Consumer endpoint: <a href={propertiesUrl}>{propertiesUrl}</a>
            </p>
          )}

          <form onSubmit={submitField}>
            <input
              placeholder="field.key"
              value={fieldForm.key}
              onChange={(e) => setFieldForm({ ...fieldForm, key: e.target.value })}
              disabled={!selected || editingKey !== ""}
              required
            />
            <select
              value={fieldForm.type}
              onChange={(e) => setFieldForm({ ...fieldForm, type: e.target.value })}
              disabled={!selected}
            >
              <option value="STRING">STRING</option>
              <option value="INTEGER">INTEGER</option>
              <option value="LONG">LONG</option>
              <option value="BOOLEAN">BOOLEAN</option>
            </select>
            <input
              placeholder="value"
              value={fieldForm.value}
              onChange={(e) => setFieldForm({ ...fieldForm, value: e.target.value })}
              disabled={!selected}
              required
            />
            <div className="actions">
              <button type="submit" disabled={!selected}>
                {editingKey ? "Update field" : "Add field"}
              </button>
              {editingKey && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingKey("");
                    setFieldForm({ key: "", type: "STRING", value: "" });
                  }}
                >
                  Cancel edit
                </button>
              )}
            </div>
          </form>

          <table className="table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Type</th>
                <th>Value</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {(doc?.fields || []).map((field) => (
                <tr key={field.key}>
                  <td>{field.key}</td>
                  <td>{field.type}</td>
                  <td>{String(field.value)}</td>
                  <td className="actions">
                    <button type="button" className="secondary" onClick={() => startEdit(field)}>
                      Edit
                    </button>
                    <button type="button" className="danger" onClick={() => removeField(field.key)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
