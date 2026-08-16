package rs.simovic.hyperdebloat;

interface IDebloatService {
    String exec(String command) = 1;
    void destroy() = 16777114;
}
