const express = require("express");
const cors = require("cors");

const faultRoutes = require("./routes/faultRoutes");

const app = express();

app.use(cors());
app.use(express.json());

app.use("/api/faults", faultRoutes);

app.get("/", (req, res) => {
    res.json({
        message: "Fault Finder API is running"
    });
});

const PORT = 3000;

app.listen(PORT, () => {
    console.log('Server running on port ${PORT}');
});
