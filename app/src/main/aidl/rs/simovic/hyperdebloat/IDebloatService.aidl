package rs.simovic.hyperdebloat;

interface IDebloatService {
    String exec(String command);
    void destroy() = 16777114;
}
