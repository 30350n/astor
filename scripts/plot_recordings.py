import matplotlib
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import argparse

def read_file(filename):
    with open(filename, "r") as file:
        output = []

        layers = []
        read_rec(file, layers, -1)

        start = layers[0][0][1][0]

        for layer in layers:
            for block in layer:
                block[1][0] -= start
                block[1][1] -= start

        return layers

def read_rec(file, layers, currentLayer):
    if currentLayer == -1 and len(layers) > 0:
        return layers
    
    line = file.readline().strip()
    
    if line == "":
        raise Exception("Unexpected end of file")

    if line.endswith("Start"):
        if currentLayer == len(layers) - 1:
            layers.append([])
            padStart = layers[0][0][1][0] if layers[0] else None

        else:
            padStart = layers[currentLayer + 1][-1][1][1]

        newBlock = (line.split(" ")[1].split("Start")[0], [int(line.split(" ")[0]) / 1000, None])

        padEnd = newBlock[1][0]
        if padStart and padStart != padEnd:
            padBlock = ("unknown", [padStart, padEnd])
            layers[currentLayer + 1].append(padBlock)

        layers[currentLayer + 1].append(newBlock)
        read_rec(file, layers, currentLayer + 1)
        
        return layers

    if line.endswith("End"):
        if not layers[currentLayer][-1][0] == line.split(" ")[1].split("End")[0]:
            raise Exception("Error1 in file, line: " + line)

        layers[currentLayer][-1][1][1] = int(line.split(" ")[0]) / 1000

        read_rec(file, layers, currentLayer - 1)
        return layers

    else:
        raise Exception("Error2 in file, line: " + line)

colorDict = {
    "astor" : "grey",
    "unknown" : "white",
    "validate" : "royalblue",
    "caching" : "darkred",
    "failingTestCase" : "red",
    "regressionTest" : "limegreen",
}

def plot(plotInput, args):
    handles = []

    for item in colorDict.items():
        if item[0] in ("unknown", "sorting"):
            continue
        
        patch = mpatches.Patch(color=item[1], label=item[0])
        handles.append(patch)

    plt.figure(figsize=(16, 9), dpi=args.dpi)
    
    for i, layer in enumerate(plotInput):
        for data in layer:
            p1 = plt.barh(i, data[1][1] - data[1][0], left=data[1][0], color=colorDict[data[0]])

    plt.barh(i + 1, 10, color="white")

    plt.legend(handles=handles)
    plt.title(args.title)
    plt.xlabel("seconds")
    plt.yticks([])
    plt.savefig(args.output_path + "." + args.format, format=args.format)
    #plt.show()

parser = argparse.ArgumentParser(description="Plot a recoding.txt")
parser.add_argument("input", type=str,
                    help="path to the recording.txt")
parser.add_argument("--output_path", type=str, default="plot",
                    help="path to the output image")
parser.add_argument("--format", type=str, choices=("png", "pdf", "svg"), default="png",
                    help="format of the output image")
parser.add_argument("--title", type=str, default="plot",
                    help="title which will be displayed in the plot")
parser.add_argument("--dpi", type=int, default=100,
                    help="dpi of the image")

args = parser.parse_args()

recording = read_file(args.input)
plot(recording, args)
